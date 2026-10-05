package openccjava;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable, allocation-free lookup of the single dictionaries in one partition. */
final class SingleCodePointIndex {
    private final String[] bmp;
    private final int[] supplementaryKeys;
    private final String[] supplementaryValues;

    private SingleCodePointIndex(String[] bmp, int[] keys, String[] values) {
        this.bmp = bmp;
        this.supplementaryKeys = keys;
        this.supplementaryValues = values;
    }

    static SingleCodePointIndex build(List<DictionaryMaxlength.DictEntry> dictionaries) {
        String[] bmp = null;
        TreeMap<Integer, String> supplementary = null;
        for (DictionaryMaxlength.DictEntry dictionary : dictionaries) {
            for (Map.Entry<String, String> entry : dictionary.dict.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // The fallback consumes exactly one code point. Two BMP characters
                // and longer keys must not become fallback matches.
                if (key == null || value == null) continue;
                if (key.length() == 1) {
                    if (bmp == null) bmp = new String[Character.MAX_VALUE + 1];
                    int cp = key.charAt(0);
                    if (bmp[cp] == null) bmp[cp] = value;
                } else if (key.length() == 2
                        && Character.isSurrogatePair(key.charAt(0), key.charAt(1))) {
                    if (supplementary == null) supplementary = new TreeMap<>();
                    supplementary.putIfAbsent(key.codePointAt(0), value);
                }
            }
        }
        int count = supplementary == null ? 0 : supplementary.size();
        int[] keys = new int[count];
        String[] values = new String[count];
        if (supplementary != null) {
            int i = 0;
            for (Map.Entry<Integer, String> entry : supplementary.entrySet()) {
                keys[i] = entry.getKey();
                values[i++] = entry.getValue();
            }
        }
        return new SingleCodePointIndex(bmp, keys, values);
    }

    String get(int codePoint) {
        if (codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            return bmp == null ? null : bmp[codePoint];
        }
        int index = Arrays.binarySearch(supplementaryKeys, codePoint);
        return index < 0 ? null : supplementaryValues[index];
    }
}
