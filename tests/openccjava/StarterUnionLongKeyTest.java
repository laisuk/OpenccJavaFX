package openccjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StarterUnionLongKeyTest {
    private static String key(int cp, int units) {
        StringBuilder value = new StringBuilder(units).appendCodePoint(cp);
        while (value.length() < units) value.append('文');
        return value.toString();
    }

    private static DictionaryMaxlength.DictEntry entry(Map<String, String> values) {
        int min = Integer.MAX_VALUE, max = 0;
        for (String key : values.keySet()) {
            min = Math.min(min, key.length());
            max = Math.max(max, key.length());
        }
        return new DictionaryMaxlength.DictEntry(values, max, min);
    }

    private static String convert(String input, List<DictionaryMaxlength.DictEntry> dicts, StarterUnion union) {
        return new DictRefs(dicts, union).applySegmentReplace(input, new OpenCC("s2t")::segmentReplaceWithUnion);
    }

    @ParameterizedTest
    @ValueSource(ints = {63, 64, 65, 80, 300})
    void bmpBoundaryAndLongOnlyStarters(int length) {
        String source = key('中', length);
        List<DictionaryMaxlength.DictEntry> dicts = Collections.singletonList(entry(Collections.singletonMap(source, "hit")));
        StarterUnion union = StarterUnion.build(dicts);
        assertTrue(union.hasStarter('中'));
        assertEquals(length, union.maxLen('中'));
        assertEquals(length < 64 ? 1L << length : 0L, union.lenMask('中'));
        assertEquals("hit", convert(source, dicts, union));
        assertEquals(source.substring(0, length - 1), convert(source.substring(0, length - 1), dicts, union));
    }

    @ParameterizedTest
    @ValueSource(ints = {63, 64, 65, 80, 300})
    void supplementaryLengthsAreUtf16Units(int length) {
        int cp = 0x20000;
        String source = key(cp, length);
        List<DictionaryMaxlength.DictEntry> dicts = Collections.singletonList(entry(Collections.singletonMap(source, "astral")));
        StarterUnion union = StarterUnion.build(dicts);
        assertEquals(length, source.length());
        assertEquals(length - 1, source.codePointCount(0, source.length()));
        assertEquals(length, union.maxLen(cp));
        assertEquals(length < 64 ? 1L << length : 0L, union.lenMask(cp));
        assertEquals("astral", convert(source, dicts, union));
    }

    @Test
    void mergesLengthsInEitherOrderWithoutInflatingOtherStarters() {
        Map<String, String> shortValues = new HashMap<>();
        shortValues.put(key('中', 3), "short");
        shortValues.put(key('中', 63), "boundary");
        shortValues.put(key(0x20000, 3), "astral short");
        shortValues.put(key('他', 2), "other");
        shortValues.put(key(0x20001, 2), "other astral");
        Map<String, String> longValues = new HashMap<>();
        longValues.put(key('中', 80), "long");
        longValues.put(key(0x20000, 300), "astral long");
        DictionaryMaxlength.DictEntry shortDict = entry(shortValues), longDict = entry(longValues);
        for (List<DictionaryMaxlength.DictEntry> dicts : Arrays.asList(
                Arrays.asList(shortDict, longDict), Arrays.asList(longDict, shortDict))) {
            StarterUnion union = StarterUnion.build(dicts);
            assertEquals(80, union.maxLen('中'));
            assertEquals(300, union.maxLen(0x20000));
            assertEquals(2, union.maxLen('他'));
            assertEquals(2, union.maxLen(0x20001));
            assertEquals((1L << 3) | (1L << 63), union.lenMask('中'));
            assertEquals(1L << 3, union.lenMask(0x20000));
            assertEquals("long", convert(key('中', 80), dicts, union));
            assertEquals("short", convert(key('中', 3), dicts, union));
            assertEquals("boundary", convert(key('中', 63), dicts, union));
        }
    }

    @Test
    void longestMatchAndDictionaryPrecedenceRemainAuthoritative() {
        String source = key('中', 80);
        Map<String, String> first = new HashMap<>();
        first.put(source.substring(0, 65), "shorter");
        first.put(source, "first");
        List<DictionaryMaxlength.DictEntry> dicts = Arrays.asList(entry(first),
                entry(Collections.singletonMap(source, "second")));
        assertEquals("first", convert(source, dicts, StarterUnion.build(dicts)));
        Collections.reverse(dicts);
        assertEquals("second", convert(source, dicts, StarterUnion.build(dicts)));
        assertEquals(source.substring(0, 64), convert(source.substring(0, 64), dicts, StarterUnion.build(dicts)));
    }

    @Test
    void maximumIsNotAnAssumedHitAndSegmentsRemainSeparate() {
        String source = key('中', 80);
        List<DictionaryMaxlength.DictEntry> dicts = Collections.singletonList(entry(Collections.singletonMap(source, "hit")));
        StarterUnion union = StarterUnion.build(dicts);
        String miss = source.substring(0, 79) + "字";
        assertEquals(miss, convert(miss, dicts, union));
        String split = source.substring(0, 40) + " " + source.substring(40);
        assertEquals(split, convert(split, dicts, union));
        assertEquals(0, union.maxLen('无'));
        assertEquals(0, union.maxLen(-1));
        assertEquals(0, union.maxLen(0x110000));
    }

    @Test
    void compatibilityConstructorDoesNotInventAnUnsafeLongKeyCap() {
        BitSet bmp = new BitSet();
        bmp.set('中');
        StarterUnion union = new StarterUnion(bmp, new BitSet(), new long[65536], Collections.emptyMap());
        assertEquals(Integer.MAX_VALUE, union.maxLen('中'));
        String source = key('中', 80);
        List<DictionaryMaxlength.DictEntry> dicts = Collections.singletonList(entry(Collections.singletonMap(source, "hit")));
        assertEquals("hit", convert(source, dicts, union));
        bmp.clear();
        assertTrue(union.hasStarter('中'));
    }

    @ParameterizedTest
    @EnumSource(CustomDictMode.class)
    void customAppendAndOverrideUseCachedPlansAndPunctuation(CustomDictMode mode) {
        String source = key('X', 80);
        OpenCC cc = new OpenCC("s2t", Collections.singletonList(CustomDictSpec.fromPairs(
                DictSlot.STPhrases, Collections.singletonMap(source, "mapped"), mode)));
        assertEquals("mapped", cc.s2t(source, false));
        assertEquals("mapped", cc.s2t(source, false)); // Reuses prepared metadata.
        assertEquals("「mapped」", cc.s2t("“" + source + "”", true));
    }

    @Test
    void subsequentConfigurationRoundAlsoMatchesLongKeys() {
        String source = key('X', 80), intermediate = key('Y', 65);
        OpenCC cc = new OpenCC("s2twp", Arrays.asList(
                CustomDictSpec.fromPairs(DictSlot.STPhrases, Collections.singletonMap(source, intermediate), CustomDictMode.Override),
                CustomDictSpec.fromPairs(DictSlot.TWVariantsPhrases, Collections.singletonMap(intermediate, "mapped"), CustomDictMode.Override)));
        assertEquals("mapped", cc.convert(source));
    }
}
