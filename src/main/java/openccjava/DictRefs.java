package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Encapsulates a multi-round reference to OpenCC dictionary entries and applies them in order
 * via a user-defined segment replacement function.
 *
 * <p>This class supports up to 3 transformation rounds, each using one or more dictionaries.
 * The current apply methods execute these rounds in sequence using either raw round dictionaries or cached partitions.
 *
 * <p>Utility constants such as punctuation maps, delimiter sets, and strip regex are also defined
 * for use in Chinese variant normalization and segmentation logic.</p>
 */
public class DictRefs {
    private final DictPartition partition1;
    private DictPartition partition2;
    private DictPartition partition3;

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

    // --- NEW: fluent round setters that also attach unions ---

    /**
     * Constructs a {@code DictRefs} for round&nbsp;1 only.
     *
     * @param r1     the list of dictionary entries for round&nbsp;1
     * @param union1 the starter union for round&nbsp;1
     */
    public DictRefs(List<DictEntry> r1, StarterUnion union1) {
        List<DictEntry> round1 = Collections.unmodifiableList(new ArrayList<>(r1));
        this.u1 = union1;
        this.partition1 = partitionDicts(round1, union1);
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
        List<DictEntry> round2 = Collections.unmodifiableList(new ArrayList<>(r2));
        this.u2 = union2;
        this.partition2 = partitionDicts(round2, union2);
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
        List<DictEntry> round3 = Collections.unmodifiableList(new ArrayList<>(r3));
        this.u3 = union3;
        this.partition3 = partitionDicts(round3, union3);
        return this;
    }

    /**
     * Groups dictionary entries into phrase dictionaries and single-character dictionaries,
     * with cached phrase-length bounds and round-level metadata.
     *
     * <p>This structure is built once per round inside {@link DictRefs}, so repeated
     * conversions do not need to repartition the same dictionaries.</p>
     */
    public static final class DictPartition {
        final List<DictEntry> phraseDicts;
        final List<DictEntry> singleDicts;
        final int phraseMaxLen;
        final int phraseMinLen;
        final int roundMaxLen;
        final StarterUnion union;

        DictPartition(List<DictEntry> phraseDicts,
                      List<DictEntry> singleDicts,
                      int phraseMaxLen,
                      int phraseMinLen,
                      int roundMaxLen,
                      StarterUnion union) {
            this.phraseDicts = phraseDicts;
            this.singleDicts = singleDicts;
            this.phraseMaxLen = phraseMaxLen;
            this.phraseMinLen = phraseMinLen;
            this.roundMaxLen = roundMaxLen;
            this.union = union;
        }
    }

    /**
     * Partitions a round of dictionary entries once and caches the derived lookup metadata.
     *
     * @param dicts the dictionaries to partition
     * @param union the starter union associated with the round
     * @return cached partition metadata for the round
     */
    private static DictPartition partitionDicts(List<DictEntry> dicts, StarterUnion union) {
        List<DictEntry> phrase = new ArrayList<>(dicts.size());
        List<DictEntry> single = new ArrayList<>(Math.min(dicts.size(), 2));

        int phraseMax = 0;
        int phraseMin = Integer.MAX_VALUE;
        int roundMax = 0;

        for (DictEntry e : dicts) {
            if (e.maxLength > roundMax) {
                roundMax = e.maxLength;
            }
            if (e.maxLength >= 3) {
                phrase.add(e);
                if (e.maxLength > phraseMax) {
                    phraseMax = e.maxLength;
                }
                if (e.minLength < phraseMin) {
                    phraseMin = e.minLength;
                }
            } else {
                single.add(e);
            }
        }

        if (phrase.isEmpty()) {
            phraseMin = 0;
        }

        return new DictPartition(
                Collections.unmodifiableList(phrase),
                Collections.unmodifiableList(single),
                phraseMax,
                phraseMin,
                roundMax,
                union
        );
    }

    /**
     * Performs multi-round segment replacement with per-round starter unions.
     * <p>
     * Each round is applied in sequence:</p>
     * <ol>
     *   <li>Round&nbsp;1 is always applied with {@link #u1}.</li>
     *   <li>If defined, round&nbsp;2 is applied with {@link #u2}.</li>
     *   <li>If defined, round&nbsp;3 is applied with {@link #u3}.</li>
     * </ol>
     *
     * <p>
     * The provided {@link SegmentReplaceFnWithUnion} receives:</p>
     * <ul>
     *   <li>the current input text,</li>
     *   <li>the cached partition for the round, including phrase/single splits,</li>
     *   <li>the cached round metadata, including the round maximum phrase length,</li>
     *   <li>and the corresponding {@link StarterUnion}.</li>
     * </ul>
     *
     * @param input     the text to process
     * @param segmentFn the function that performs replacement using cached round metadata
     * @return the fully converted string after all rounds
     */
    public String applySegmentReplace(String input, SegmentReplaceFnWithUnion segmentFn) {
        String result = segmentFn.apply(input, partition1);
        if (partition2 != null) {
            result = segmentFn.apply(result, partition2);
        }
        if (partition3 != null) {
            result = segmentFn.apply(result, partition3);
        }
        return result;
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
         * Applies dictionary-based transformation to the given input text
         * using the cached round partition and starter union metadata.
         *
         * @param input     the text to convert
         * @param partition the cached partition metadata for this round
         * @return the transformed result
         */
        String apply(String input, DictPartition partition);
    }

    /**
     * A pattern for stripping non-Chinese symbols (punctuation, whitespace, Latin letters, digits, etc.).
     *
     * <p>This is used in heuristics such as {@code zhoCheck()} to isolate Chinese text for variant checking.</p>
     */
    public static final Pattern STRIP_REGEX = Pattern.compile("[!-/:-@\\[-`{-~\\t\\n\\v\\f\\r 0-9A-Za-z_著]");

    /**
     * A predefined set of characters used as delimiters for text segmentation.
     */
    public static final Set<Character> DELIMITERS;

    /**
     * 65,536-bit lookup table for delimiters in the BMP (U+0000 to U+FFFF).
     * Each long holds 64 characters.
     */
    private static final long[] DELIM_BMP = new long[1024];

    static {
        // Use LinkedHashSet to preserve order (optional, but deterministic)
        Set<Character> s = new LinkedHashSet<>(Arrays.asList(
                ' ', '\t', '\n', '\r', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
                ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '{', '|', '}', '~', '＝', '、', '。',
                '﹁', '﹂', '—', '－', '（', '）', '《', '》', '〈', '〉', '？', '！', '…', '／', '＼', '︒', '︑', '︔', '︓',
                '︿', '﹀', '︹', '︺', '︙', '︐', '［', '﹇', '］', '﹈', '︕', '︖', '︰', '︳', '︴', '︽', '︾', '︵', '︶',
                '｛', '︷', '｝', '︸', '﹃', '﹄', '【', '︻', '】', '︼', '　', '～', '．', '，', '；', '：'
        ));
        DELIMITERS = Collections.unmodifiableSet(s);
        // Fill the bitset
        for (char c : s) {
            int idx = (int) c >>> 6;      // which long
            int bit = (int) c & 63;       // which bit in the long
            DELIM_BMP[idx] |= (1L << bit);
        }
    }

    /**
     * Bit-level delimiter check: O(1) and allocation-free.
     *
     * @param c the character to test
     * @return true if {@code c} is a delimiter
     */
    public static boolean isDelimiter(char c) {
        return ((DELIM_BMP[(int) c >>> 6] >>> ((int) c & 63)) & 1L) != 0L;
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
