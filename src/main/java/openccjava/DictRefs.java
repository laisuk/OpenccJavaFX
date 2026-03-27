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
 * for use in Chinese variant normalization and segmentation logic.
 */
public class DictRefs {
    private final List<DictEntry> round1;
    private List<DictEntry> round2;
    private List<DictEntry> round3;
    private List<Integer> maxLengths;
    private final DictPartition p1;
    private DictPartition p2;
    private DictPartition p3;

    /**
     * Starter union for round-1 dictionaries.
     */
    public StarterUnion u1;

    /**
     * Starter union for round 2 dictionaries.
     */
    public StarterUnion u2;

    /**
     * Starter union for round 3 dictionaries.
     */
    public StarterUnion u3;

    /**
     * Constructs a {@code DictRefs} for round 1 only.
     *
     * @param r1     the list of dictionary entries for round 1
     * @param union1 the starter union for round 1
     */
    public DictRefs(List<DictEntry> r1, StarterUnion union1) {
        this.round1 = Collections.unmodifiableList(new ArrayList<>(r1));
        this.round2 = null;
        this.round3 = null;
        this.maxLengths = null;
        this.u1 = union1;
        this.p1 = partitionDicts(this.round1);
        this.p2 = null;
        this.p3 = null;
    }

    /**
     * Adds round 2 to this {@code DictRefs}.
     *
     * @param r2     the list of dictionary entries for round 2
     * @param union2 the starter union for round 2
     * @return this {@code DictRefs} instance, for fluent chaining
     */
    public DictRefs withRound2(List<DictEntry> r2, StarterUnion union2) {
        this.round2 = Collections.unmodifiableList(new ArrayList<>(r2));
        this.u2 = union2;
        this.p2 = partitionDicts(this.round2);
        this.maxLengths = null;
        return this;
    }

    /**
     * Adds round 3 to this {@code DictRefs}.
     *
     * @param r3     the list of dictionary entries for round 3
     * @param union3 the starter union for round 3
     * @return this {@code DictRefs} instance, for fluent chaining
     */
    public DictRefs withRound3(List<DictEntry> r3, StarterUnion union3) {
        this.round3 = Collections.unmodifiableList(new ArrayList<>(r3));
        this.u3 = union3;
        this.p3 = partitionDicts(this.round3);
        this.maxLengths = null;
        return this;
    }

    /**
     * Computes the maximum phrase length from each round's dictionaries.
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
                maxLengths.add(max);
            }
        }
        return maxLengths;
    }

    /**
     * Performs multi-round segment replacement with cached per-round dictionary partitions.
     *
     * @param input     the text to process
     * @param segmentFn the function that performs replacement using a cached partition and union
     * @return the fully converted string after all rounds
     */
    public String applySegmentReplace(String input, SegmentReplaceFnWithUnion segmentFn) {
        return applyRounds(input, segmentFn::apply, p1, p2, p3);
    }

    private <T> String applyRounds(String input,
                                   RoundApplier<T> applier,
                                   T r1,
                                   T r2,
                                   T r3) {
        List<Integer> lengths = getMaxLengths();

        String result = applier.apply(input, r1, lengths.get(0), u1);
        if (r2 != null) {
            result = applier.apply(result, r2, lengths.get(1), u2);
        }
        if (r3 != null) {
            result = applier.apply(result, r3, lengths.get(2), u3);
        }
        return result;
    }

    private interface RoundApplier<T> {
        String apply(String input, T round, int maxLength, StarterUnion union);
    }

    @FunctionalInterface
    public interface SegmentReplaceFnWithUnion {
        String apply(String input, DictPartition part, int maxLength, StarterUnion union);
    }

    /**
     * Groups dictionary entries into phrase and single-character dictionaries,
     * with cached phrase-length bounds.
     */
    public static final class DictPartition {
        final List<DictEntry> phraseDicts;
        final List<DictEntry> singleDicts;
        final int phraseMaxLen;
        final int phraseMinLen;

        DictPartition(List<DictEntry> phraseDicts,
                      List<DictEntry> singleDicts,
                      int phraseMaxLen,
                      int phraseMinLen) {
            this.phraseDicts = phraseDicts;
            this.singleDicts = singleDicts;
            this.phraseMaxLen = phraseMaxLen;
            this.phraseMinLen = phraseMinLen;
        }
    }

    static DictPartition partitionDicts(List<DictEntry> dicts) {
        List<DictEntry> phrase = new ArrayList<>(dicts.size());
        List<DictEntry> single = new ArrayList<>(2);

        int phraseMax = 0;
        int phraseMin = Integer.MAX_VALUE;

        for (DictEntry e : dicts) {
            if (e.maxLength >= 3) {
                phrase.add(e);
                if (e.maxLength > phraseMax) phraseMax = e.maxLength;
                if (e.minLength < phraseMin) phraseMin = e.minLength;
            } else {
                single.add(e);
            }
        }

        if (phrase.isEmpty()) {
            phraseMax = 0;
            phraseMin = 0;
        }

        return new DictPartition(
                Collections.unmodifiableList(phrase),
                Collections.unmodifiableList(single),
                phraseMax,
                phraseMin
        );
    }

    /**
     * A pattern for stripping non-Chinese symbols (punctuation, whitespace, Latin letters, digits, etc.).
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
        Set<Character> s = new LinkedHashSet<>(Arrays.asList(
                ' ', '\t', '\n', '\r', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
                ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '{', '|', '}', '~', '＝', '、', '。',
                '﹁', '﹂', '—', '－', '（', '）', '《', '》', '〈', '〉', '？', '！', '…', '／', '＼', '︒', '︑', '︔', '︓',
                '︿', '﹀', '︹', '︺', '︙', '︐', '［', '﹇', '］', '﹈', '︕', '︖', '︰', '︳', '︴', '︽', '︾', '︵', '︶',
                '｛', '︷', '｝', '︸', '﹃', '﹄', '【', '︻', '】', '︼', '　', '～', '．', '，', '；', '：'
        ));
        DELIMITERS = Collections.unmodifiableSet(s);
        for (char c : s) {
            int idx = (int) c >>> 6;
            int bit = (int) c & 63;
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


