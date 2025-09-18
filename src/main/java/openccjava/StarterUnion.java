package openccjava;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Immutable union of "starter" characters used in a single conversion round.
 * <p>
 * A starter is defined as the first Unicode code point of each dictionary key.
 * By precomputing the set of all possible starters, conversion can quickly
 * check whether a substring may match any dictionary entry before attempting
 * a full lookup.
 * </p>
 *
 * <p>
 * Internally, two {@link BitSet}s are maintained:
 * <ul>
 *   <li>{@code bmpMask} – for all starters in the Basic Multilingual Plane
 *       (U+0000 to U+FFFF)</li>
 *   <li>{@code astralMask} – for all starters in the Supplementary Planes
 *       (U+10000 to U+10FFFF), stored as an offset from {@code BMP_LIMIT}</li>
 * </ul>
 * </p>
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
     * Creates a new {@code StarterUnion} with the given bit masks.
     *
     * @param bmp    the BMP mask (U+0000–U+FFFF)
     * @param astral the astral mask (U+10000–U+10FFFF)
     */
    private StarterUnion(BitSet bmp, BitSet astral) {
        this.bmpMask = bmp;
        this.astralMask = astral;
    }

    /**
     * Builds a {@code StarterUnion} from the given list of dictionary entries.
     * <p>
     * Only the first code point of each dictionary key is scanned.
     * For each such starter:
     * <ul>
     *   <li>If it lies in the BMP, it is recorded in {@code bmpMask}.</li>
     *   <li>If it lies in the astral planes, it is recorded in {@code astralMask}
     *       at offset {@code cp - BMP_LIMIT}.</li>
     * </ul>
     * </p>
     *
     * @param dicts the dictionary entries to scan
     * @return an immutable {@code StarterUnion} containing all starters
     */
    public static StarterUnion build(List<DictionaryMaxlength.DictEntry> dicts) {
        final BitSet bmp = new BitSet(BMP_LIMIT);
        final BitSet astral = new BitSet((UNICODE_MAX - BMP_LIMIT) + 1);

        for (DictionaryMaxlength.DictEntry d : dicts) {
            final Map<String, String> map = d.dict;
            for (String k : map.keySet()) {
                if (k == null || k.isEmpty()) continue;
                final int cp = codePointAt(k, 0);
                if (cp < 0) continue;
                if (cp < BMP_LIMIT) bmp.set(cp);
                else if (cp <= UNICODE_MAX) astral.set(cp - BMP_LIMIT);
            }
        }
        return new StarterUnion(bmp, astral);
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
     * Returns the number of UTF-16 code units required to represent the given starter.
     * <p>
     * This is used when advancing through a {@link CharSequence} during conversion.
     * <ul>
     *   <li>For BMP characters, the return value is {@code 1} (a single {@code char}).</li>
     *   <li>For non-BMP characters (astral planes), the return value is {@code 2}
     *       (a surrogate pair).</li>
     * </ul>
     * </p>
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
