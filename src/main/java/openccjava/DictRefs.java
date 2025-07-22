package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Encapsulates a multi-round reference to OpenCC dictionary entries and applies them in order
 * via a user-defined segment replacement function.
 *
 * <p>This class supports up to 3 transformation rounds, each using one or more dictionaries.
 * The {@link #applySegmentReplace(String, SegmentReplaceFn)} method applies these rounds in sequence.
 *
 * <p>Utility constants such as punctuation maps, delimiter sets, and strip regex are also defined
 * for use in Chinese variant normalization and segmentation logic.
 */
public class DictRefs {
    private final List<DictEntry> round1;
    private List<DictEntry> round2;
    private List<DictEntry> round3;
    private List<Integer> maxLengths;

    /**
     * Constructs a {@code DictRefs} instance with round 1 dictionary(s).
     *
     * @param round1 list of dictionary entries to apply in the first round
     */
    public DictRefs(List<DictEntry> round1) {
        this.round1 = round1;
        this.round2 = null;
        this.round3 = null;
        this.maxLengths = null;
    }

    /**
     * Sets round 2 dictionaries and clears cached maxLengths.
     *
     * @param round2 list of dictionary entries for round 2
     * @return {@code this}, for method chaining
     */
    public DictRefs withRound2(List<DictEntry> round2) {
        this.round2 = round2;
        this.maxLengths = null;
        return this;
    }

    /**
     * Sets round 3 dictionaries and clears cached maxLengths.
     *
     * @param round3 list of dictionary entries for round 3
     * @return {@code this}, for method chaining
     */
    public DictRefs withRound3(List<DictEntry> round3) {
        this.round3 = round3;
        this.maxLengths = null;
        return this;
    }

    /**
     * Computes the maximum phrase length from each round's dictionaries.
     *
     * <p>The result is cached until {@code withRound2()} or {@code withRound3()} is called again.
     *
     * @return a list of max lengths for each round
     */
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
                maxLengths.add(max); // Always adds a value per round
            }
        }
        return maxLengths;
    }

    /**
     * Applies all defined dictionary rounds using the provided segment replacement function.
     *
     * <p>Each round applies its dictionary list sequentially on the result of the previous round.
     *
     * @param input     the input text to convert
     * @param segmentFn a functional interface that accepts (input, dicts, maxLength)
     * @return the transformed result after all applicable rounds
     */
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

    /**
     * A functional interface for segment-based dictionary replacement.
     */
    @FunctionalInterface
    public interface SegmentReplaceFn {
        /**
         * Applies dictionary-based transformation to a text segment.
         *
         * @param input     the text to convert
         * @param dicts     dictionaries to use for conversion
         * @param maxLength maximum phrase length allowed
         * @return the transformed result
         */
        String apply(String input, List<DictEntry> dicts, int maxLength);
    }

    /**
     * Finds the maximum number of characters that can be encoded within the given UTF-8 byte budget.
     *
     * <p>Used to efficiently limit the preview of text when detecting language variants (e.g. in zhoCheck).
     *
     * @param input    the input string
     * @param maxBytes the max number of UTF-8 bytes allowed
     * @return number of characters (not bytes) fitting within {@code maxBytes}
     */
    public static int findMaxUtf8Length(String input, int maxBytes) {
        if (input == null || input.isEmpty() || maxBytes <= 0) {
            return 0;
        }

        int estMaxChars = Math.min(input.length(), maxBytes / 2);  // Assume avg 2 bytes per char
        input = input.substring(0, estMaxChars);

        int left = 0, right = input.length();
        while (left < right) {
            int mid = (left + right + 1) / 2;
            int utf8Length = input.substring(0, mid).getBytes(StandardCharsets.UTF_8).length;
            if (utf8Length <= maxBytes) {
                left = mid;
            } else {
                right = mid - 1;
            }
        }

        return left;
    }

    /**
     * A pattern for stripping non-Chinese symbols (punctuation, whitespace, Latin letters, digits, etc.).
     *
     * <p>This is used in heuristics such as {@code zhoCheck()} to isolate Chinese text for variant checking.
     */
    public static final Pattern STRIP_REGEX = Pattern.compile("[!-/:-@\\[-`{-~\\t\\n\\v\\f\\r 0-9A-Za-z_著]");

    /**
     * A predefined set of characters used as delimiters for text segmentation.
     */
    public static final Set<Character> DELIMITERS = new HashSet<>(List.of(
            ' ', '\t', '\n', '\r', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
            ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '{', '|', '}', '~', '＝', '、', '。', '“', '”',
            '‘', '’', '『', '』', '「', '」', '﹁', '﹂', '—', '－', '（', '）', '《', '》', '〈', '〉', '？', '！', '…', '／',
            '＼', '︒', '︑', '︔', '︓', '︿', '﹀', '︹', '︺', '︙', '︐', '［', '﹇', '］', '﹈', '︕', '︖', '︰', '︳',
            '︴', '︽', '︾', '︵', '︶', '｛', '︷', '｝', '︸', '﹃', '﹄', '【', '︻', '】', '︼', '　', '～', '．', '，',
            '；', '：'
    ));

    /**
     * Mapping of Simplified-style punctuation to Traditional-style punctuation.
     */
    public static final Map<Character, Character> PUNCT_S2T_MAP = Map.of(
            '“', '「',
            '”', '」',
            '‘', '『',
            '’', '』'
    );

    /**
     * Mapping of Traditional-style punctuation to Simplified-style punctuation.
     */
    public static final Map<Character, Character> PUNCT_T2S_MAP = Map.of(
            '「', '“',
            '」', '”',
            '『', '‘',
            '』', '’'
    );
}
