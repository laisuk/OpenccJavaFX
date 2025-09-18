package openccjava;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Immutable union of starters for one conversion round.
 */
public final class StarterUnion {
    private static final int BMP_LIMIT = 0x10000;
    private static final int UNICODE_MAX = 0x10FFFF;

    private final BitSet bmpMask;    // U+0000 to U+FFFF
    private final BitSet astralMask; // U+10000 to U+10FFFF, stored as (cp - 0x10000)

    private StarterUnion(BitSet bmp, BitSet astral) {
        this.bmpMask = bmp;
        this.astralMask = astral;
    }

    /**
     * Build union from the round's dicts (only scans the first code point of each key).
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
     * Fast check whether any dict in this round starts with the given code point.
     */
    public boolean hasStarter(int codePoint) {
        if (codePoint < 0) return false;
        if (codePoint < BMP_LIMIT) return bmpMask.get(codePoint);
        if (codePoint <= UNICODE_MAX) return astralMask.get(codePoint - BMP_LIMIT);
        return false;
    }

    /**
     * UTF-16 code units to advance for this starter: 1 for BMP (“BMA”), 2 for non-BMP.
     */
    public int starterIndexLen(int codePoint) {
        return codePoint < BMP_LIMIT ? 1 : 2;
    }

    /**
     * CharSequence-safe code point reader.
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
