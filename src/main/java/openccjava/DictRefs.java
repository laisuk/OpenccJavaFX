package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

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

    // --- NEW: unions per round (nullable when not used) ---

    /**
     * Starter union for round-1 dictionaries.
     * <p>
     * Contains all possible starter characters (first code points)
     * for the dictionaries in the first round.
     * May be {@code null} if the round is not defined.
     * </p>
     */
    public StarterUnion u1;

    /**
     * Starter union for round 2 dictionaries.
     * <p>
     * Contains all possible starter characters (first code points)
     * for the dictionaries in the second round.
     * May be {@code null} if the round is not used.
     * </p>
     */
    public StarterUnion u2;

    /**
     * Starter union for round 3 dictionaries.
     * <p>
     * Contains all possible starter characters (first code points)
     * for the dictionaries in the third round.
     * May be {@code null} if the round is not used.
     * </p>
     */
    public StarterUnion u3;

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

    // --- NEW: fluent round setters that also attach unions ---

    /**
     * Constructs a {@code DictRefs} for round&nbsp;1 only.
     *
     * @param r1     the list of dictionary entries for round&nbsp;1
     * @param union1 the starter union for round&nbsp;1
     */
    public DictRefs(List<DictEntry> r1, StarterUnion union1) {
        this.round1 = Collections.unmodifiableList(new ArrayList<>(r1));
        this.u1 = union1;
    }

    /**
     * Adds round&nbsp;2 to this {@code DictRefs}.
     * <p>
     * The provided dictionary entries are stored as an unmodifiable list,
     * and the corresponding starter union is attached to {@link #u2}.
     * </p>
     *
     * @param r2     the list of dictionary entries for round&nbsp;2
     * @param union2 the starter union for round&nbsp;2
     * @return this {@code DictRefs} instance, for fluent chaining
     */
    public DictRefs withRound2(List<DictEntry> r2, StarterUnion union2) {
        this.round2 = Collections.unmodifiableList(new ArrayList<>(r2));
        this.u2 = union2;
        return this;
    }

    /**
     * Adds round&nbsp;3 to this {@code DictRefs}.
     * <p>
     * The provided dictionary entries are stored as an unmodifiable list,
     * and the corresponding starter union is attached to {@link #u3}.
     * </p>
     *
     * @param r3     the list of dictionary entries for round&nbsp;3
     * @param union3 the starter union for round&nbsp;3
     * @return this {@code DictRefs} instance, for fluent chaining
     */
    public DictRefs withRound3(List<DictEntry> r3, StarterUnion union3) {
        this.round3 = Collections.unmodifiableList(new ArrayList<>(r3));
        this.u3 = union3;
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
     * Performs multi-round segment replacement with per-round starter unions.
     * <p>
     * Each round is applied in sequence:
     * <ol>
     *   <li>Round&nbsp;1 is always applied with {@link #u1}.</li>
     *   <li>If defined, round&nbsp;2 is applied with {@link #u2}.</li>
     *   <li>If defined, round&nbsp;3 is applied with {@link #u3}.</li>
     * </ol>
     * </p>
     *
     * <p>
     * The provided {@link SegmentReplaceFnWithUnion} receives:
     * <ul>
     *   <li>the current input text,</li>
     *   <li>the list of dictionaries for the round,</li>
     *   <li>the maximum phrase length for that round,</li>
     *   <li>and the corresponding {@link StarterUnion}.</li>
     * </ul>
     * </p>
     *
     * @param input     the text to process
     * @param segmentFn the function that performs replacement using dictionaries and union
     * @return the fully converted string after all rounds
     */
    public String applySegmentReplace(String input, SegmentReplaceFnWithUnion segmentFn) {
        List<Integer> maxLengths = getMaxLengths();

        String result = segmentFn.apply(input, round1, maxLengths.get(0), u1);
        if (round2 != null) {
            result = segmentFn.apply(result, round2, maxLengths.get(1), u2);
        }
        if (round3 != null) {
            result = segmentFn.apply(result, round3, maxLengths.get(2), u3);
        }
        return result;
    }

    // --- Back-compat overload (optional). Calls with null unions. ---

    /**
     * Backward-compatible overload of {@link #applySegmentReplace(String, SegmentReplaceFnWithUnion)}.
     * <p>
     * This version accepts a {@link SegmentReplaceFn} that does not handle
     * {@link StarterUnion}. The unions are passed as {@code null}.
     * </p>
     *
     * @param input     the text to process
     * @param segmentFn the function that performs replacement using dictionaries only
     * @return the fully converted string after all rounds
     */
    public String applySegmentReplace(String input, SegmentReplaceFn segmentFn) {
        return applySegmentReplace(input, (txt, dicts, maxLen, union) -> segmentFn.apply(txt, dicts, maxLen));
    }

    /**
     * A functional interface for segment-based dictionary replacement.
     * <p>
     * This variant is unaware of starter unions. It is suitable for
     * simple replacements where only the dictionaries and maximum
     * phrase length are required.
     * </p>
     */
    @FunctionalInterface
    public interface SegmentReplaceFn {
        /**
         * Applies dictionary-based transformation to the given input text.
         *
         * @param input     the text to convert
         * @param dicts     the dictionaries to use for conversion
         * @param maxLength the maximum phrase length allowed
         * @return the transformed result
         */
        String apply(String input, List<DictEntry> dicts, int maxLength);
    }

    /**
     * A union-aware functional interface for segment-based dictionary replacement.
     * <p>
     * This extended variant is used by
     * {@link DictRefs#applySegmentReplace(String, SegmentReplaceFnWithUnion)}
     * and provides access to a per-round {@link StarterUnion}, enabling
     * optimized starter checks during replacement.
     * </p>
     */
    @FunctionalInterface
    public interface SegmentReplaceFnWithUnion {
        /**
         * Applies dictionary-based transformation to the given input text,
         * using both dictionaries and the provided starter union.
         *
         * @param input     the text to convert
         * @param dicts     the dictionaries to use for conversion
         * @param maxLength the maximum phrase length allowed
         * @param union     the starter union for this round
         * @return the transformed result
         */
        String apply(String input, List<DictEntry> dicts, int maxLength, StarterUnion union);
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
    public static final Set<Character> DELIMITERS;

    static {
        // Use LinkedHashSet to preserve order (optional, but deterministic)
        Set<Character> s = new LinkedHashSet<>(Arrays.asList(
                ' ', '\t', '\n', '\r', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
                ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '{', '|', '}', '~', '＝', '、', '。', '“', '”',
                '‘', '’', '『', '』', '「', '」', '﹁', '﹂', '—', '－', '（', '）', '《', '》', '〈', '〉', '？', '！', '…', '／',
                '＼', '︒', '︑', '︔', '︓', '︿', '﹀', '︹', '︺', '︙', '︐', '［', '﹇', '］', '﹈', '︕', '︖', '︰', '︳',
                '︴', '︽', '︾', '︵', '︶', '｛', '︷', '｝', '︸', '﹃', '﹄', '【', '︻', '】', '︼', '　', '～', '．', '，',
                '；', '：'
        ));
        DELIMITERS = Collections.unmodifiableSet(s);
    }

    /**
     * Mapping of Simplified-style punctuation to Traditional-style punctuation.
     */
    public static final Map<Character, Character> PUNCT_S2T_MAP;

    static {
        Map<Character, Character> m = new LinkedHashMap<>();
        m.put('“', '「');
        m.put('”', '」');
        m.put('‘', '『');
        m.put('’', '』');
        PUNCT_S2T_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Mapping of Traditional-style punctuation to Simplified-style punctuation.
     */
    public static final Map<Character, Character> PUNCT_T2S_MAP;

    static {
        Map<Character, Character> m = new LinkedHashMap<>();
        m.put('「', '“');
        m.put('」', '”');
        m.put('『', '‘');
        m.put('』', '’');
        PUNCT_T2S_MAP = Collections.unmodifiableMap(m);
    }
}
