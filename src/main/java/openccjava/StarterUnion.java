package openccjava;

import java.util.*;

/**
 * Immutable union of "starter" characters used in a single conversion round.
 * <p>
 * A starter is defined as the first Unicode code point of each dictionary key.
 * By precomputing the set of all possible starters, conversion can quickly
 * check whether a substring may match any dictionary entry before attempting
 * a full lookup.
 * </p>
 *
 * <p>Internally, two {@link BitSet}s are maintained:</p>
 *
 * <ul>
 *   <li>{@code bmpMask} – for all starters in the Basic Multilingual Plane
 *       (U+0000 to U+FFFF)</li>
 *   <li>{@code astralMask} – for all starters in the Supplementary Planes
 *       (U+10000 to U+10FFFF), stored as an offset from {@code BMP_LIMIT}</li>
 * </ul>
 *
 * <p>
 * Instances of this class are immutable and thread-safe once constructed.
 * </p>
 */
public final class StarterUnion {
    /**
     * Maximum code point value for the Basic Multilingual Plane (U+0000–U+FFFF).
     */
    private static final int BMP_LIMIT = 0x10000;
    /**
     * Maximum Unicode code point value (U+10FFFF).
     */
    private static final int UNICODE_MAX = 0x10FFFF;

    /**
     * Starter mask for BMP characters.
     */
    private final BitSet bmpMask;    // U+0000 to U+FFFF
    /**
     * Starter mask for supplementary (astral) characters, offset by {@code BMP_LIMIT}.
     */
    private final BitSet astralMask; // U+10000 to U+10FFFF, stored as (cp - 0x10000)

    /**
     * NEW: Per-starter length masks for fast phrase lookup.
     *
     * <p>For each possible starter code point, these masks record which key lengths
     * (in UTF-16 code units) are present in the loaded dictionaries:</p>
     *
     * <ul>
     *   <li><b>{@code bmpLenMask}</b> – fixed-size array indexed by BMP code point
     *       value (U+0000 to U+FFFF). Each entry is a 64-bit bitmask where bit {@code L}
     *       is set if there exists a dictionary key of length {@code L} starting with
     *       that code point.</li>
     *   <li><b>{@code astralLenMask}</b> – sparse map for astral plane code points
     *       (U+10000 to U+10FFFF). Each entry maps a code point to its 64-bit length mask,
     *       using the same encoding as {@code bmpLenMask}.</li>
     * </ul>
     *
     * <p>This allows the conversion loop to skip impossible substring lengths for a
     * given starter character, avoiding wasted {@link String#substring(int, int)} calls
     * and hash lookups. Keys longer than 63 UTF-16 units are ignored for bit-masking
     * purposes.</p>
     */
    private final long[] bmpLenMask;              // BMP cp -> bitmask of supported lengths
    private final Map<Integer, Long> astralLenMask; // astral cp -> bitmask

    /**
     * Creates a new {@code StarterUnion} with the given presence and length masks.
     *
     * <p>This constructor is normally invoked by {@link #build(List)} after scanning
     * all dictionary keys. It encapsulates both starter presence (which code points
     * can begin a key) and per-starter length masks (which substring lengths are valid
     * for that starter).</p>
     *
     * @param bmpMask       bit mask of starter presence in the Basic Multilingual Plane
     *                      (U+0000–U+FFFF); a set bit means at least one key starts
     *                      with that code point
     * @param astralMask    bit mask of starter presence in the astral planes
     *                      (U+10000–U+10FFFF), offset by {@code BMP_LIMIT}
     * @param bmpLenMask    array of per-starter length masks for BMP code points;
     *                      {@code bmpLenMask[cp]} is a 64-bit bitmask where bit
     *                      {@code L} is set if a dictionary key of length {@code L}
     *                      starts with code point {@code cp}
     * @param astralLenMask sparse map of astral code points (U+10000–U+10FFFF) to
     *                      their 64-bit length masks, using the same encoding as
     *                      {@code bmpLenMask}
     */
    public StarterUnion(BitSet bmpMask, BitSet astralMask,
                        long[] bmpLenMask,
                        Map<Integer, Long> astralLenMask) {
        this.bmpMask = bmpMask;
        this.astralMask = astralMask;
        this.bmpLenMask = bmpLenMask;
        this.astralLenMask = Collections.unmodifiableMap(astralLenMask);
    }

    /**
     * Builds a {@code StarterUnion} from the given list of dictionary entries.
     *
     * <p>Only the first code point of each dictionary key is scanned. For each such starter:</p>
     * <ul>
     *   <li>If it lies in the BMP (U+0000–U+FFFF), it is recorded in {@code bmpMask}.</li>
     *   <li>If it lies in the astral planes (U+10000–U+10FFFF), it is recorded in
     *       {@code astralMask} at offset {@code cp - BMP_LIMIT}.</li>
     *   <li>The key length (in UTF-16 code units) is also encoded into a 64-bit
     *       per-starter length mask:
     *       <ul>
     *         <li>For BMP starters, {@code bmpLenMask[cp]} holds a bitmask where
     *             bit {@code L} is set if a key of length {@code L} begins with
     *             code point {@code cp}.</li>
     *         <li>For astral starters, {@code astralLenMask} maps the code point
     *             to its 64-bit length mask, using the same encoding as
     *             {@code bmpLenMask}.</li>
     *       </ul>
     *       Keys of length ≥ 64 UTF-16 units are ignored for bit-masking purposes.</li>
     * </ul>
     *
     * <p>This enables the conversion loop to quickly reject impossible starters and
     * impossible substring lengths before attempting expensive lookups.</p>
     *
     * @param dicts the dictionary entries to scan
     * @return an immutable {@code StarterUnion} containing all starters and their length masks
     */
    public static StarterUnion build(List<DictionaryMaxlength.DictEntry> dicts) {
        final BitSet bmp = new BitSet(BMP_LIMIT);
        final BitSet astral = new BitSet((UNICODE_MAX - BMP_LIMIT) + 1);
        final long[] bmpLen = new long[BMP_LIMIT];
        final Map<Integer, Long> astralLen = new HashMap<>();

        for (DictionaryMaxlength.DictEntry d : dicts) {
            final Map<String, String> map = d.dict;
            for (String k : map.keySet()) {
                if (k == null || k.isEmpty()) continue;
                final int cp = Character.codePointAt(k, 0);
                if (cp < 0 || cp > UNICODE_MAX) continue;

                // presence
                if (cp < BMP_LIMIT) bmp.set(cp);
                else astral.set(cp - BMP_LIMIT);

                // length bit (guard lengths >=64 to keep mask in a long)
                final int L = k.length(); // UTF-16 units (astral counts as 2)
                if (L >= 64) continue;

                final long bit = 1L << L;
                if (cp < BMP_LIMIT) {
                    bmpLen[cp] |= bit;
                } else {
                    astralLen.merge(cp, bit, (a, b) -> a | b);
                }
            }
        }

        return new StarterUnion(bmp, astral, bmpLen, astralLen);
    }

    /**
     * Checks whether any dictionary entry in this round starts with the given code point.
     * <p>
     * This is a fast pre-filter: if the code point is not present in the starter set,
     * then no dictionary entry can possibly match a substring beginning with that code point.
     * </p>
     *
     * @param codePoint the Unicode code point to test
     * @return {@code true} if at least one dictionary key starts with this code point;
     * {@code false} otherwise
     */
    public boolean hasStarter(int codePoint) {
        if (codePoint < 0) return false;
        if (codePoint < BMP_LIMIT) return bmpMask.get(codePoint);
        if (codePoint <= UNICODE_MAX) return astralMask.get(codePoint - BMP_LIMIT);
        return false;
    }

    /**
     * Returns the precomputed length bitmask for the given starter code point.
     *
     * <p>The mask encodes, in a single {@code long}, which substring lengths
     * (measured in UTF-16 code units) are possible for dictionary keys starting
     * with this code point. Bit {@code L} is set if at least one key of length
     * {@code L} begins with {@code cp}. Keys longer than 63 UTF-16 units are not
     * represented in the mask.</p>
     *
     * <p>This allows the conversion loop to quickly skip impossible lengths before
     * building substrings or performing hash lookups.</p>
     *
     * @param cp the Unicode code point to query
     * @return a 64-bit length mask; {@code 0} if no keys are known to start with {@code cp}
     */
    public long lenMask(int cp) {
        if (cp < BMP_LIMIT) return bmpLenMask[cp];
        return astralLenMask.getOrDefault(cp, 0L);
    }

    /**
     * Returns the number of UTF-16 code units required to represent the given starter.
     *
     * <p>This is used when advancing through a {@link CharSequence} during conversion.</p>
     * <ul>
     *   <li>For BMP characters, the return value is {@code 1} (a single {@code char}).</li>
     *   <li>For non-BMP characters (astral planes), the return value is {@code 2}
     *       (a surrogate pair).</li>
     * </ul>
     *
     * @param codePoint the Unicode code point
     * @return {@code 1} for BMP characters, {@code 2} for non-BMP characters
     */
    public int starterIndexLen(int codePoint) {
        return codePoint < BMP_LIMIT ? 1 : 2;
    }

    /**
     * Reads the Unicode code point at the specified index of a {@link CharSequence}.
     * <p>
     * This method is safe for both {@link String} and generic {@code CharSequence} inputs.
     * If the character at the index is a high surrogate and the next character is a
     * valid low surrogate, the pair is combined into a supplementary code point.
     * Otherwise, the single {@code char} value is returned.
     * </p>
     *
     * @param s     the sequence from which to read
     * @param index the index of the first code unit
     * @return the Unicode code point starting at the given index
     * @throws IndexOutOfBoundsException if {@code index} is outside the sequence range
     */
    public static int codePointAt(CharSequence s, int index) {
        if (s instanceof String) return ((String) s).codePointAt(index);
        final char c1 = s.charAt(index);
        if (Character.isHighSurrogate(c1) && index + 1 < s.length()) {
            final char c2 = s.charAt(index + 1);
            if (Character.isLowSurrogate(c2)) {
                return Character.toCodePoint(c1, c2);
            }
        }
        return c1;
    }
}
