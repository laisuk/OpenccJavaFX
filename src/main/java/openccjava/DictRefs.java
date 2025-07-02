package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class DictRefs {
    private final List<DictEntry> round1;
    private List<DictEntry> round2;
    private List<DictEntry> round3;
    private List<Integer> maxLengths;

    public DictRefs(List<DictEntry> round1) {
        this.round1 = round1;
        this.round2 = null;
        this.round3 = null;
        this.maxLengths = null;
    }

    public DictRefs withRound2(List<DictEntry> round2) {
        this.round2 = round2;
        this.maxLengths = null;
        return this;
    }

    public DictRefs withRound3(List<DictEntry> round3) {
        this.round3 = round3;
        this.maxLengths = null;
        return this;
    }

    private List<Integer> getMaxLengths() {
        if (maxLengths == null) {
            maxLengths = new ArrayList<>();
            for (List<DictEntry> round : Arrays.asList(round1, round2, round3)) {
                int max = 0;
                if (round != null) {
                    for (DictEntry entry : round) {
                        max = Math.max(max, entry.maxLength);
                    }
                }
                maxLengths.add(max); // always adds one value per round
            }
        }
        return maxLengths;
    }

    public String applySegmentReplace(String input, SegmentReplaceFn segmentFn) {
        List<Integer> maxLengths = getMaxLengths();
        String result = segmentFn.apply(input, round1, maxLengths.get(0));
        if (round2 != null) {
            result = segmentFn.apply(result, round2, maxLengths.get(1));
        }
        if (round3 != null) {
            result = segmentFn.apply(result, round3, maxLengths.get(2));
        }
        return result;
    }

    @FunctionalInterface
    public interface SegmentReplaceFn {
        String apply(String input, List<DictEntry> dicts, int maxLength);
    }

    public static int findMaxUtf8Length(String input, int maxBytes) {
        int left = 0, right = input.length();
        while (left < right) {
            int mid = (left + right + 1) / 2;
            String substr = input.substring(0, Math.min(mid, input.length()));
            int utf8Length = substr.getBytes(StandardCharsets.UTF_8).length;
            if (utf8Length <= maxBytes) {
                left = mid;
            } else {
                right = mid - 1;
            }
        }
        return left;
    }

    public static final Pattern STRIP_REGEX = Pattern.compile("[!-/:-@\\[-`{-~\\t\\n\\v\\f\\r 0-9A-Za-z_著]");

    public static final Set<Character> DELIMITERS = new HashSet<>(List.of(
            ' ', '\t', '\n', '\r', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
            ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '{', '|', '}', '~', '＝', '、', '。', '“', '”',
            '‘', '’', '『', '』', '「', '」', '﹁', '﹂', '—', '－', '（', '）', '《', '》', '〈', '〉', '？', '！', '…', '／',
            '＼', '︒', '︑', '︔', '︓', '︿', '﹀', '︹', '︺', '︙', '︐', '［', '﹇', '］', '﹈', '︕', '︖', '︰', '︳',
            '︴', '︽', '︾', '︵', '︶', '｛', '︷', '｝', '︸', '﹃', '﹄', '【', '︻', '】', '︼', '　', '～', '．', '，',
            '；', '：'
    ));

    public static final Map<Character, Character> PUNCT_S2T_MAP = Map.of(
            '“', '「',
            '”', '」',
            '‘', '『',
            '’', '』'
    );

    public static final Map<Character, Character> PUNCT_T2S_MAP = Map.of(
            '「', '“',
            '」', '”',
            '『', '‘',
            '』', '’'
    );
}