package openccjava;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SingleCodePointIndexTest {
    private static DictionaryMaxlength.DictEntry entry(Map<String, String> values) {
        int max = 0, min = Integer.MAX_VALUE;
        for (String key : values.keySet()) {
            if (key == null) continue;
            max = Math.max(max, key.length());
            min = Math.min(min, key.length());
        }
        return new DictionaryMaxlength.DictEntry(values, max, min == Integer.MAX_VALUE ? 0 : min);
    }

    @Test
    void preservesFirstNonNullDictionaryHitAndEmptyReplacement() {
        Map<String, String> first = new HashMap<>();
        first.put("汉", "漢");
        first.put("字", "");
        first.put("空", null);
        Map<String, String> second = new HashMap<>();
        second.put("汉", "later");
        second.put("字", "later");
        second.put("空", "fallback");
        SingleCodePointIndex index = SingleCodePointIndex.build(Arrays.asList(entry(first), entry(second)));
        assertEquals("漢", index.get('汉'));
        assertEquals("", index.get('字'));
        assertEquals("fallback", index.get('空'));
        assertNull(index.get('未'));
    }

    @Test
    void supplementaryLookupPreservesPrecedenceAndHandlesBothEnds() {
        String low = new String(Character.toChars(0x10000));
        String high = new String(Character.toChars(0x10FFFF));
        Map<String, String> first = new HashMap<>();
        first.put(low, "first");
        first.put(high, "last");
        SingleCodePointIndex index = SingleCodePointIndex.build(Arrays.asList(
                entry(first), entry(Collections.singletonMap(low, "later"))));
        assertEquals("first", index.get(0x10000));
        assertEquals("last", index.get(0x10FFFF));
        assertNull(index.get(0x20000));
        assertNull(index.get('汉'));
    }

    @Test
    void fallbackNeverMatchesTwoBmpCharacters() {
        Map<String, String> singles = new HashMap<>();
        singles.put("汉字", "must not match");
        singles.put("汉", "漢");
        DictRefs refs = new DictRefs(Collections.singletonList(entry(singles)), null);
        assertEquals("漢字", refs.applySegmentReplace("汉字", new OpenCC("s2t")::segmentReplaceWithUnion));
    }

    @Test
    void phraseHitStillWinsOverIndexedSingle() {
        DictRefs refs = new DictRefs(Arrays.asList(
                entry(Collections.singletonMap("汉字词", "phrase")),
                entry(Collections.singletonMap("汉", "single"))), null);
        assertEquals("phrase single", refs.applySegmentReplace("汉字词 汉", new OpenCC("s2t")::segmentReplaceWithUnion));
    }

    @Test
    void validPairsAreConsumedTogetherWhileIsolatedSurrogatesRemainMatchable() {
        String pair = new String(Character.toChars(0x20000));
        Map<String, String> singles = new HashMap<>();
        singles.put(pair, "pair");
        singles.put("\uD840", "high");
        singles.put("\uDC00", "low");
        DictRefs refs = new DictRefs(Collections.singletonList(entry(singles)), null);
        assertEquals("pair highXlow", refs.applySegmentReplace(pair + " \uD840X\uDC00",
                new OpenCC("s2t")::segmentReplaceWithUnion));
    }

    @Test
    void emptyIndexHasNoMatches() {
        SingleCodePointIndex index = SingleCodePointIndex.build(Collections.emptyList());
        assertNull(index.get('汉'));
        assertNull(index.get(0x20000));
    }
}
