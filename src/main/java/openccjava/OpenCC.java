package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import static openccjava.DictRefs.isDelimiter;


/**
 * OpenCC is a pure Java implementation of the Open Chinese Convert (OpenCC) system.
 * It provides simplified-traditional Chinese text conversion using preloaded dictionaries.
 *
 * <p>This class supports multiple configurations such as s2t, t2s, s2tw, tw2s, etc.
 * It loads dictionaries from a bundled JSON file or falls back to raw dict text files.
 *
 * <p>Conversion is stateless and thread-safe, with thread-local StringBuilder optimization
 * for performance.
 */
public class OpenCC {
    /**
     * Internal logger used for diagnostic and fallback messages.
     * Logging is disabled by default to avoid console output in GUI applications.
     */
    private static final Logger LOGGER = Logger.getLogger(OpenCC.class.getName());

    static {
        // Disable logging by default
        LOGGER.setLevel(Level.OFF);
    }

    /**
     * Enables or disables verbose logging for OpenCC.
     * <p>
     * When enabled, the logger will print informational messages about dictionary loading,
     * fallback behavior, and other diagnostics to the console or log handler.
     * When disabled (default), logging is suppressed to avoid cluttering GUI or user-facing environments.
     * </p>
     *
     * @param enabled {@code true} to enable logging, {@code false} to disable it
     */
    public static void setVerboseLogging(boolean enabled) {
        LOGGER.setLevel(enabled ? Level.INFO : Level.OFF);
    }

    /**
     * The loaded dictionary data, including all phrase and character maps
     * along with their maximum lengths.
     */
    private final DictionaryMaxlength dictionary;

    /**
     * Cache of conversion plans keyed by configuration and punctuation flag.
     * <p>
     * Each plan contains one or more dictionary rounds and their
     * precomputed {@link StarterUnion}s for fast lookup.
     * </p>
     */
    private final ConversionPlanCache planCache;

    /**
     * Supported conversion configurations.
     * <p>
     * Each constant corresponds to an OpenCC conversion mode, such as
     * Simplified↔Traditional, or region-specific variants (Taiwan, Hong Kong, Japan).
     * </p>
     */
    public enum Config {
        S2T, T2S, S2Tw, Tw2S, S2Twp, Tw2Sp,
        S2Hk, Hk2S, T2Tw, T2Twp, Tw2T, Tw2Tp,
        T2Hk, Hk2T, T2Jp, Jp2T;

        /**
         * Returns the lowercase string form of this config.
         * <p>
         * Example: {@code S2T.asStr()} → {@code "s2t"}.
         * </p>
         *
         * @return the lowercase string representation
         */
        public String asStr() {
            return name().toLowerCase();
        }

        /**
         * Parses a string into a {@code Config} constant, case-insensitive.
         *
         * @param value a string such as {@code "s2t"} or {@code "T2S"}
         * @return the matching {@code Config} constant
         * @throws IllegalArgumentException if {@code value} is {@code null} or does not match any constant
         */
        public static Config fromStr(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Config string cannot be null");
            }
            return Config.valueOf(value.trim().toUpperCase());
        }
    }

    /**
     * Cached DictRefs to avoid redundant config resolution.
     */
    private final Map<String, DictRefs> configCache = new HashMap<>();

    /**
     * Set of characters considered as delimiters during segmentation.
     */
    private static final Set<Character> delimiters = DictRefs.DELIMITERS;

    /**
     * Current conversion configuration key (e.g., s2t, t2s).
     */
    private String config = "s2t";

    /**
     * Stores the last error message encountered, if any.
     */
    private String lastError;

    /**
     * Maximum capacity for thread-local StringBuilder (used during conversion).
     */
    private static final int MAX_SB_CAPACITY = 1024;

    /**
     * Thread-local StringBuilder to minimize memory allocations.
     */
    private static final ThreadLocal<StringBuilder> threadLocalSb =
            ThreadLocal.withInitial(() -> new StringBuilder(MAX_SB_CAPACITY));

    /**
     * Provides a lazily initialized, thread-safe singleton instance of
     * {@link DictionaryMaxlength}.
     *
     * <p>This holder follows the <em>Initialization-on-demand holder idiom</em>:
     * the dictionary is not loaded until the first call to {@link #get()}.
     * This guarantees that:</p>
     * <ul>
     *   <li>The dictionary is loaded exactly once per JVM.</li>
     *   <li>Access is thread-safe without requiring explicit synchronization.</li>
     *   <li>Subsequent calls to {@code get()} return the same shared instance.</li>
     * </ul>
     *
     * <p>The dictionary is loaded in the following order:</p>
     * <ol>
     *   <li><b>JSON file from the file system:</b>
     *       {@code dicts/dictionary_maxlength.json} in the current working directory.</li>
     *   <li><b>Embedded JSON resource:</b>
     *       {@code /dicts/dictionary_maxlength.json} from the application’s classpath
     *       (e.g. inside the JAR).</li>
     *   <li><b>Plain text fallback:</b>
     *       If neither JSON source is found, falls back to loading individual
     *       dictionary text files via {@link DictionaryMaxlength#fromDicts()}.</li>
     * </ol>
     *
     * <p>If all attempts fail, a {@link RuntimeException} is thrown.</p>
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * DictionaryMaxlength dict = OpenCC.DictionaryHolder.get();
     * }</pre>
     */
    public static final class DictionaryHolder {
        private DictionaryHolder() {
        }

        /**
         * Internal holder for the lazily initialized singleton.
         */
        private static class Holder {
            private static final DictionaryMaxlength DEFAULT = load();
        }

        /**
         * Returns the shared singleton {@link DictionaryMaxlength} instance.
         * The instance is created on first invocation.
         *
         * @return the shared dictionary instance
         * @throws RuntimeException if dictionary loading fails
         */
        public static DictionaryMaxlength get() {
            return Holder.DEFAULT;
        }

        /**
         * Attempts to load the dictionary from JSON (file system or classpath),
         * falling back to plain-text sources if necessary.
         *
         * @return a fully loaded {@link DictionaryMaxlength}
         * @throws RuntimeException if no dictionary source can be loaded
         */
        private static DictionaryMaxlength load() {
            try {
                Path jsonPath = Paths.get("dicts", "dictionary_maxlength.json");
                if (Files.exists(jsonPath)) {
                    return DictionaryMaxlength.fromJson(jsonPath.toString());
                }
                try (InputStream in = DictionaryMaxlength.class.getResourceAsStream(
                        "/dicts/dictionary_maxlength.json")) {
                    if (in != null) return DictionaryMaxlength.fromJson(in);
                    return DictionaryMaxlength.fromDicts();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load dictionaries", e);
            }
        }
    }

    /**
     * Constructs an OpenCC instance using the default configuration ("s2t").
     */
    public OpenCC() {
        this("s2t");
    }

    /**
     * Constructs an {@code OpenCC} instance with the specified conversion configuration.
     *
     * <p>This constructor does not reload dictionaries for each instance.
     * Instead, it retrieves a shared singleton {@link DictionaryMaxlength}
     * from {@link DictionaryHolder}. The dictionary is loaded lazily on
     * first access and reused by all subsequent {@code OpenCC} instances.</p>
     *
     * <p>The shared dictionary is resolved in the following order (on first access only):</p>
     * <ol>
     *   <li><b>JSON file from the file system:</b>
     *       {@code dicts/dictionary_maxlength.json} in the current working directory.</li>
     *   <li><b>Embedded JSON resource:</b>
     *       {@code /dicts/dictionary_maxlength.json} from the application’s classpath
     *       (e.g. inside the JAR).</li>
     *   <li><b>Plain-text fallback:</b>
     *       If neither JSON source is found, falls back to loading individual dictionary
     *       text files from {@code dicts/} via {@link DictionaryMaxlength#fromDicts()}.</li>
     * </ol>
     *
     * <p><b>Important:</b> because the dictionary is a shared singleton,
     * any modification to its contents (e.g. adding or removing entries)
     * will affect <em>all</em> {@code OpenCC} instances within the JVM.</p>
     *
     * <p>If all loading attempts fail, a {@link RuntimeException} is thrown
     * with the underlying cause recorded in {@link #lastError}.</p>
     *
     * @param config the conversion configuration key (e.g. {@code "s2t"}, {@code "tw2sp"});
     *               see {@link Config} for supported values
     * @throws RuntimeException if no dictionary source can be loaded or parsed
     */
    public OpenCC(String config) {
        this.dictionary = DictionaryHolder.get(); // Lazy static, loaded on first access
        this.planCache = new ConversionPlanCache(() -> this.dictionary);

        setConfig(config);
    }

    /**
     * Constructs an OpenCC instance using plain text dictionaries from the given directory.
     * <p>
     * <strong>Deprecated:</strong> This constructor will be removed in the next major version.
     * Use {@link #OpenCC(String)} instead.
     * </p>
     *
     * @param config   the conversion configuration key to use
     * @param dictPath the path to the text dictionary directory
     * @deprecated Use {@link #OpenCC(String)} instead. This overload will be removed in the next major version.
     */
    @Deprecated
    public OpenCC(String config, Path dictPath) {
        DictionaryMaxlength loaded;
        try {
            loaded = DictionaryMaxlength.fromDicts(dictPath.toString());
        } catch (Exception e) {
            this.lastError = e.getMessage();
            throw new RuntimeException("Failed to load text dictionaries from: " + dictPath, e);
        }

        this.dictionary = loaded;
        this.planCache = new ConversionPlanCache(() -> this.dictionary); // after dictionary

        setConfig(config);
    }

    /**
     * Retrieves the {@link DictRefs} for the given configuration and punctuation mode,
     * including attached {@link StarterUnion}s.
     *
     * @param cfg         the conversion configuration
     * @param punctuation whether punctuation conversion is enabled
     * @return the prepared {@link DictRefs} for this configuration
     */
    private DictRefs getRefsUnionForConfig(Config cfg, boolean punctuation) {
        return planCache.getPlan(cfg, punctuation);
    }

    /**
     * Clears all cached conversion plans.
     * <p>
     * This forces plans to be rebuilt lazily the next time they are requested.
     * Starter unions inside {@link DictionaryMaxlength} remain unaffected.
     * </p>
     */
    public void clearPlanCache() {
        planCache.clear();
    }

    /**
     * Sets the current conversion configuration if supported.
     * Defaults to "s2t" on invalid input and sets an error message.
     *
     * @param config the new configuration key
     */
    public void setConfig(String config) {
        if (getSupportedConfigs().contains(config)) {
            this.config = config;
        } else {
            this.lastError = "Invalid config: " + config;
            this.config = "s2t";
        }
    }

    /**
     * Returns the current conversion configuration.
     *
     * @return the configuration key
     */
    public String getConfig() {
        return config;
    }

    /**
     * Returns the most recent error message encountered.
     *
     * @return the last error message, or null if none
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Returns the list of supported configuration keys.
     *
     * @return list of supported configs
     */
    public static List<String> getSupportedConfigs() {
        return Collections.unmodifiableList(Arrays.asList("s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
                "t2tw", "tw2t", "t2twp", "tw2tp", "t2hk", "hk2t", "t2jp", "jp2t"));
    }

    /**
     * Converts the given input text using the current configuration.
     * Punctuation conversion is disabled by default.
     *
     * @param input the text to convert
     * @return the converted result
     */
    public String convert(String input) {
        return convert(input, false); // default punctuation = false
    }

    /**
     * Converts the given input text using the current configuration.
     *
     * <p>The method dispatches the conversion to the corresponding
     * configuration logic (e.g., s2t, t2s, s2tw, etc.). If the configuration
     * is unsupported, it returns an error string and sets {@code lastError}.
     *
     * @param input       the text to convert
     * @param punctuation whether to convert punctuation characters
     * @return the converted result, or an error string if config is invalid
     */
    public String convert(String input, boolean punctuation) {
        if (input == null || input.isEmpty()) {
            lastError = "Input text is null or empty";
            return lastError;
        }

        String result;
        switch (config) {
            case "s2t":
                result = s2t(input, punctuation);
                break;
            case "t2s":
                result = t2s(input, punctuation);
                break;
            case "s2tw":
                result = s2tw(input, punctuation);
                break;
            case "tw2s":
                result = tw2s(input, punctuation);
                break;
            case "s2twp":
                result = s2twp(input, punctuation);
                break;
            case "tw2sp":
                result = tw2sp(input, punctuation);
                break;
            case "s2hk":
                result = s2hk(input, punctuation);
                break;
            case "hk2s":
                result = hk2s(input, punctuation);
                break;
            case "t2tw":
                result = t2tw(input);
                break;
            case "t2twp":
                result = t2twp(input);
                break;
            case "tw2t":
                result = tw2t(input);
                break;
            case "tw2tp":
                result = tw2tp(input);
                break;
            case "t2hk":
                result = t2hk(input);
                break;
            case "hk2t":
                result = hk2t(input);
                break;
            case "t2jp":
                result = t2jp(input);
                break;
            case "jp2t":
                result = jp2t(input);
                break;
            default:
                lastError = "Unsupported config: " + config;
                result = lastError;
                break;
        }

        return result;
    }

    /**
     * Retrieves the {@link DictRefs} for a given conversion configuration key.
     *
     * <p>This method checks the internal cache first. If no entry is found,
     * it creates a new {@code DictRefs} object using the relevant dictionary entries
     * from {@link DictionaryMaxlength}, supporting up to 3 rounds of replacements.
     *
     * <p>The result is cached for future lookups to avoid recomputation.
     *
     * @param key the configuration key (e.g., "s2t", "tw2sp")
     * @return a {@code DictRefs} instance representing the translation rules,
     * or {@code null} if the key is unsupported
     */
    private DictRefs getDictRefs(String key) {
        if (configCache.containsKey(key)) return configCache.get(key);

        final DictionaryMaxlength d = dictionary; // no 'var' in Java 8
        DictRefs refs = null;

        switch (key) {
            case "s2t":
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters));
                break;

            case "t2s":
                refs = new DictRefs(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            case "s2tw":
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.tw_variants));
                break;

            case "tw2s":
                refs = new DictRefs(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            case "s2twp":
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.tw_phrases))
                        .withRound3(Collections.singletonList(d.tw_variants));
                break;

            case "tw2sp":
                refs = new DictRefs(Arrays.asList(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            case "s2hk":
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.hk_variants));
                break;

            case "hk2s":
                refs = new DictRefs(Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            case "t2jp":
                refs = new DictRefs(Collections.singletonList(d.jp_variants));
                break;

            case "jp2t":
                refs = new DictRefs(Arrays.asList(d.jps_phrases, d.jps_characters, d.jp_variants_rev));
                break;

            default:
                // unknown key -> leave refs null
                break;
        }

        if (refs != null) configCache.put(key, refs);
        return refs;
    }

    /**
     * Applies dictionary-based replacements to the input text using segment-based processing.
     *
     * <p>If the text contains multiple segments (e.g., based on punctuation/delimiters),
     * each segment is processed separately. Large inputs are processed in parallel for performance.
     *
     * @param text      the original text to convert
     * @param dicts     the list of dictionaries (in order of priority) to apply
     * @param maxLength the maximum phrase length to match in the dictionaries
     * @return the converted text
     */
    public String segmentReplace(String text, List<DictEntry> dicts, int maxLength) {
        if (text == null || text.isEmpty()) return text;

        List<int[]> ranges = getSplitRanges(text, true);
        int numSegments = ranges.size();

        // Fast path: entire text is one uninterrupted segment
        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == text.length()) {
            return convertSegment(text, dicts, maxLength);
        }

        // Use parallel stream if input is large or highly segmented
        boolean useParallel = text.length() > 10_000 || numSegments > 100;
        int sbCapacity = text.length() + (text.length() >> 4);
        StringBuilder sb = new StringBuilder(sbCapacity);

        if (useParallel) {
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String segment = text.substring(range[0], range[1]);
                segments[i] = convertSegment(segment, dicts, maxLength);
            });

            // Join all converted segments
            for (String seg : segments) {
                sb.append(seg);
            }
        } else {
            // Fallback: sequential processing
            for (int[] range : ranges) {
                String segment = text.substring(range[0], range[1]);
                sb.append(convertSegment(segment, dicts, maxLength));
            }
        }
        return sb.toString();
    }

    /**
     * Converts a single segment using the specified dictionaries with longest-match-first strategy.
     *
     * <p>If the segment is a single delimiter character, it is returned as-is.
     * Otherwise, the method performs greedy matching from left to right,
     * trying to match the longest possible substrings found in the dictionaries.
     *
     * <p>This method reuses a thread-local {@code StringBuilder} to reduce allocations.
     *
     * @param segment   the text segment to convert
     * @param dicts     the list of dictionaries to apply (highest priority first)
     * @param maxLength the maximum phrase length to consider
     * @return the converted segment
     */
    public static String convertSegment(String segment, List<DictEntry> dicts, int maxLength) {
        if (segment.length() == 1 && isDelimiter(segment.charAt(0))) {
            return segment;
        }

        int segLen = segment.length();
        StringBuilder sb = threadLocalSb.get();
        sb.setLength(0); // reset for reuse
        sb.ensureCapacity(segLen + (segLen >> 4));  // ~+6.25%

        int i = 0;
        while (i < segLen) {
            int bestLen = 0;
            String bestMatch = null;
            int maxScanLen = Math.min(maxLength, segLen - i);

            for (int len = maxScanLen; len > 0; len--) {
                final int end = i + len;
                String word = segment.substring(i, end);
                for (DictEntry entry : dicts) {
                    if (entry.maxLength < len || entry.minLength > len) continue;

                    String value = entry.dict.get(word);
                    if (value != null) {
                        bestMatch = value;
                        bestLen = len;
                        break; // found match, stop searching this word
                    }
                }
                if (bestMatch != null) break; // found match, move to next position
            }

            if (bestMatch != null) {
                sb.append(bestMatch);
                i += bestLen;
            } else {
                sb.append(segment.charAt(i));
                i++;
            }
        }

        return sb.toString();
    }

    /**
     * Splits the input text into a list of index ranges based on delimiter characters.
     *
     * <p>This method is used to divide long text into smaller segments for conversion.
     * A segment is defined as a contiguous run of non-delimiter characters, optionally
     * followed by or isolated with a delimiter depending on the {@code inclusive} flag.
     *
     * <p>Each returned {@code int[]} contains two elements:
     * [start index (inclusive), end index (exclusive)].
     *
     * @param text      the input text to segment
     * @param inclusive whether to include delimiters as part of the preceding segment ({@code true})
     *                  or as separate segments ({@code false})
     * @return a list of (start, end) index ranges representing the segments
     */
    private static List<int[]> getSplitRanges(String text, boolean inclusive) {
        List<int[]> result = new ArrayList<>();
        int start = 0;
        final int textLength = text.length(); // Cache text length

        for (int i = 0; i < textLength; i++) {
            if (isDelimiter(text.charAt(i))) {
                if (inclusive) {
                    // Optimized: Directly add the inclusive range
                    result.add(new int[]{start, i + 1});
                } else {
                    // Optimized: Avoid adding empty ranges if i == start
                    if (i > start) {
                        result.add(new int[]{start, i});     // before delimiter
                    }
                    result.add(new int[]{i, i + 1});         // delimiter itself
                }
                start = i + 1; // Update start for the next segment
            }
        }

        // Add the last segment if it exists
        if (start < textLength) {
            result.add(new int[]{start, textLength});
        }

        return result;
    }

    // --- helper struct -----------------------------------------------------------

    /**
     * Groups dictionary entries into phrase dictionaries and single-character dictionaries,
     * with cached phrase-length bounds.
     * <p>
     * This partitioning is used internally to optimize lookup:
     * <ul>
     *   <li><b>phraseDicts</b> – dictionaries where {@code maxLength &ge; 3},
     *       searched longest-first during phrase matching</li>
     *   <li><b>singleDicts</b> – dictionaries where {@code maxLength &lt; 3},
     *       typically single-character or punctuation mappings</li>
     *   <li><b>phraseMaxLen</b> – the maximum key length across all phrase dictionaries</li>
     *   <li><b>phraseMinLen</b> – the minimum key length across all phrase dictionaries
     *       (0 if none)</li>
     * </ul>
     * </p>
     */
    private static final class DictPartition {
        /**
         * Dictionaries containing multi-character phrases (length ≥ 3).
         */
        final List<DictEntry> phraseDicts;

        /**
         * Dictionaries containing single-character or punctuation entries (length < 3).
         */
        final List<DictEntry> singleDicts;

        /**
         * Maximum phrase length across {@link #phraseDicts}.
         */
        final int phraseMaxLen;

        /**
         * Minimum phrase length across {@link #phraseDicts} (0 if no phrase dicts).
         */
        final int phraseMinLen;

        /**
         * Creates a new {@code DictPartition} with the given phrase and single-character
         * dictionaries, and cached phrase length bounds.
         *
         * @param phraseDicts  dictionaries containing multi-character phrase entries
         *                     (where {@code maxLength ≥ 3}); must already be wrapped
         *                     as unmodifiable if immutability is desired
         * @param singleDicts  dictionaries containing single-character or punctuation
         *                     entries (where {@code maxLength < 3}); must already be
         *                     wrapped as unmodifiable if immutability is desired
         * @param phraseMaxLen maximum key length across all {@code phraseDicts}, or
         *                     {@code 0} if no phrase dictionaries exist
         * @param phraseMinLen minimum key length across all {@code phraseDicts}, or
         *                     {@code 0} if no phrase dictionaries exist
         */
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

    /**
     * Partitions a list of dictionary entries into phrase dictionaries and single-character dictionaries.
     *
     * <p>Entries with {@code maxLength ≥ 3} are placed in {@code phraseDicts}, and both the maximum
     * and minimum key lengths across those phrase dictionaries are tracked. Entries with
     * {@code maxLength < 3} are placed in {@code singleDicts}. Returned lists are wrapped as
     * unmodifiable to prevent accidental modification.</p>
     *
     * <p>If no phrase dictionaries are present, {@code phraseMaxLen} and {@code phraseMinLen}
     * are both normalized to {@code 0}.</p>
     *
     * @param dicts the dictionaries to partition
     * @return a {@link DictPartition} containing grouped entries and cached phrase length bounds
     */
    private static DictPartition partitionDicts(List<DictEntry> dicts) {
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
            // No phrase dicts: normalize bounds to 0
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
     * Performs dictionary-based segment replacement using a starter union and
     * phrase/single partitioning.
     *
     * <p>This method accelerates conversion by:</p>
     * <ul>
     *   <li>Partitioning the provided dictionaries into phrase dictionaries
     *       (length ≥ 3) and single-character dictionaries (length &lt; 3)
     *       via {@link #partitionDicts(List)}.</li>
     *   <li>Splitting the input text into independent ranges using
     *       {@link #getSplitRanges(String, boolean)}.</li>
     *   <li>Passing each segment to {@code convertSegmentWithUnion}, which uses
     *       the supplied {@link StarterUnion} to skip impossible starters quickly.</li>
     * </ul>
     *
     * <p>Execution mode:</p>
     * <ul>
     *   <li><b>Parallel</b> – if the input text exceeds 10,000 characters or
     *       there are more than 100 split segments, segments are processed
     *       in parallel using {@link IntStream#parallel()}.</li>
     *   <li><b>Sequential</b> – otherwise, segments are processed in order
     *       in a single thread.</li>
     * </ul>
     *
     * <p>If the text is {@code null} or empty, it is returned unchanged.</p>
     *
     * @param text      the input text
     * @param dicts     the dictionaries to use for conversion
     * @param maxLength the maximum phrase length
     * @param union     the starter union for fast starter checks
     * @return the converted text
     */
    public String segmentReplaceWithUnion(String text,
                                          List<DictEntry> dicts,
                                          int maxLength,
                                          StarterUnion union) {
        if (text == null || text.isEmpty()) return text;

        DictPartition part = partitionDicts(dicts);

        List<int[]> ranges = getSplitRanges(text, true);
        int numSegments = ranges.size();

        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == text.length()) {
            return convertSegmentWithUnion(text, part, maxLength, union);
        }

        boolean useParallel = text.length() > 1_000_000 || numSegments > 256;

        if (!useParallel) {
            return (convertSegmentWithUnion(text, part, maxLength, union));
        } else {
            int sbCapacity = text.length() + (text.length() >> 4);
            StringBuilder sb = new StringBuilder(sbCapacity);
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String seg = text.substring(range[0], range[1]);
                segments[i] = convertSegmentWithUnion(seg, part, maxLength, union);
            });

            for (String seg : segments) sb.append(seg);
            return sb.toString();
        }
    }

    // Internal: faster converter for a single segment using pre-partitioned dicts

    /**
     * Converts a single text segment using pre-partitioned dictionaries and an optional {@link StarterUnion}.
     *
     * <h3>Overview</h3>
     * This method implements the OpenCC-style greedy, phrase-first replacement algorithm with several
     * optimizations to minimize unnecessary lookups. It processes the input string left to right, emitting
     * replacements or original code points as appropriate.
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li><b>Starter pre-check:</b>
     *       If a {@link StarterUnion} is provided and the current code point is not present in its starter
     *       mask, the code point is copied directly without performing any dictionary lookups.</li>
     *   <li><b>Phrase-first search (greedy):</b>
     *       <ul>
     *         <li>Candidate lengths are bounded by both {@code phraseMaxLen}/{@code phraseMinLen}
     *             from {@link DictPartition} and the per-starter {@code lenMask} from {@code StarterUnion}.</li>
     *         <li>Lengths are tried longest-to-shortest to ensure deterministic greedy matching.</li>
     *         <li>Each dictionary entry is filtered by its {@code minLength}/{@code maxLength} to skip
     *             impossible candidates early.</li>
     *         <li>On the first hit, the replacement is appended and the cursor advances by the matched length.</li>
     *       </ul>
     *   </li>
     *   <li><b>Single-character fallback:</b>
     *       If no phrase match is found, the algorithm attempts a lookup in {@code singleDicts} using exactly
     *       the current code point (or surrogate pair). On hit, the replacement is appended and the cursor
     *       advances by one code point.</li>
     *   <li><b>No match:</b>
     *       If neither phrase nor single dictionaries contain the key, the original code point is appended and
     *       the cursor advances by one code point.</li>
     * </ol>
     *
     * <h3>Performance notes</h3>
     * <ul>
     *   <li><b>Union pre-check:</b> avoids creating substrings and hash lookups when no dictionary key can
     *       start with the current code point.</li>
     *   <li><b>Length mask:</b> skips impossible substring lengths for the current starter in O(1) time
     *       via a 64-bit bitmask lookup.</li>
     *   <li><b>Greedy longest-match:</b> ensures results are consistent with OpenCC reference behavior.</li>
     *   <li>All bounds are in UTF-16 units. Surrogate pairs therefore count as length 2 and advance by
     *       two code units.</li>
     * </ul>
     *
     * <h3>Unicode notes</h3>
     * <ul>
     *   <li>Iteration is performed by Unicode code point.</li>
     *   <li>{@link Character#charCount(int)} determines how many UTF-16 units a code point consumes.</li>
     *   <li>Substring slicing uses UTF-16 indices; keys must exactly match the input code point sequence.</li>
     *   <li>No normalization or case-folding is performed.</li>
     * </ul>
     *
     * @param input       the text segment to convert (non-null; empty string is returned immediately)
     * @param part        partitioned dictionaries ({@code phraseDicts}, {@code singleDicts})
     *                    with cached {@code phraseMaxLen} and {@code phraseMinLen}
     * @param roundMaxLen per-round maximum phrase length limit (upper-bounds the search window)
     * @param union       optional {@link StarterUnion} for fast starter rejection and length filtering;
     *                    may be {@code null}
     * @return the converted string segment
     */
    private static String convertSegmentWithUnion(
            String input,
            DictPartition part,
            int roundMaxLen,
            StarterUnion union
    ) {
        final int n = input.length();
        if (n == 0) return input;

        final StringBuilder out = new StringBuilder(n + (n >> 4));

        // Hoist these to avoid repeated virtual calls
        final boolean hasPhrases = !part.phraseDicts.isEmpty();
        final boolean hasSingles = !part.singleDicts.isEmpty();

        int i = 0;
        while (i < n) {
            // --- Starter code point (safe: we're inside a single String segment) ---
            final int cp = input.codePointAt(i);
            final int starterLen = Character.charCount(cp);

            // 1) Starter union pre-check
            if (union != null && !union.hasStarter(cp)) {
                out.appendCodePoint(cp);
                i += starterLen;
                continue;
            }

            String hit = null;
            int hitLen = 0;

            // 2) Phrase search (respects phraseMinLen, per-entry min/max, and union lenMask)
            if (hasPhrases) {
                final int remaining = n - i;
                final int tryMax = Math.min(Math.min(part.phraseMaxLen, roundMaxLen), remaining);
                if (tryMax >= 1) {
                    final int tryMin = Math.max(1, Math.min(part.phraseMinLen, remaining));
                    if (tryMax >= tryMin) {
                        final long lMask = (union != null) ? union.lenMask(cp) : ~0L; // no union ⇒ allow all
                        if (lMask != 0L) {
                            outer:
                            for (int len = tryMax; len >= tryMin; len--) {
                                // Skip impossible lengths per union mask (mask covers lengths 0..63)
                                if (len < 64 && ((lMask >>> len) & 1L) == 0L) continue;

                                final int j = i + len;
                                // Guard (should be redundant due to tryMax/remaining, but cheap & safe)
                                if (j > n) continue;

                                final String sub = input.substring(i, j);
                                for (DictEntry e : part.phraseDicts) {
                                    if (len < e.minLength || len > e.maxLength) continue;
                                    final String repl = e.dict.get(sub);
                                    if (repl != null) {
                                        hit = repl;
                                        hitLen = len;
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3) Single-char (or surrogate pair) fallback
            if (hit == null && hasSingles) {
                // i + starterLen <= n is guaranteed by how starterLen is formed,
                // but keep a minimal guard for clarity.
                final int j = i + starterLen;
                if (j <= n) {
                    final String sub = input.substring(i, j);
                    for (DictEntry e : part.singleDicts) {
                        final String repl = e.dict.get(sub);
                        if (repl != null) {
                            hit = repl;
                            hitLen = starterLen;
                            break;
                        }
                    }
                }
            }

            // 4) Emit
            if (hit != null) {
                out.append(hit);
                i += hitLen;
            } else {
                out.appendCodePoint(cp);
                i += starterLen;
            }
        }

        return out.toString();
    }

    /**
     * Converts Simplified Chinese to Traditional Chinese.
     *
     * @param input       the text in Simplified Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Traditional Chinese
     */
    public String s2t(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.S2T, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Simplified Chinese.
     *
     * @param input       the text in Traditional Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Simplified Chinese
     */
    public String t2s(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.T2S, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Simplified Chinese to Traditional Chinese (Taiwan standard).
     *
     * @param input       the text in Simplified Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Traditional Chinese (Taiwan)
     */
    public String s2tw(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.S2Tw, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese (Taiwan) to Simplified Chinese.
     *
     * @param input       the text in Traditional Chinese (Taiwan)
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Simplified Chinese
     */
    public String tw2s(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.Tw2S, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Simplified Chinese to Traditional Chinese (Taiwan with phrase and variant adjustments).
     *
     * @param input       the text in Simplified Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in full Taiwan-style Traditional Chinese
     */
    public String s2twp(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.S2Twp, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Taiwan-style Traditional Chinese to Simplified Chinese.
     *
     * @param input       the text in Taiwan Traditional Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Simplified Chinese
     */
    public String tw2sp(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.Tw2Sp, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Simplified Chinese to Traditional Chinese (Hong Kong standard).
     *
     * @param input       the text in Simplified Chinese
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Hong Kong-style Traditional Chinese
     */
    public String s2hk(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.S2Hk, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Hong Kong-style Traditional Chinese to Simplified Chinese.
     *
     * @param input       the text in Traditional Chinese (HK)
     * @param punctuation whether to also convert punctuation marks
     * @return the converted text in Simplified Chinese
     */
    public String hk2s(String input, boolean punctuation) {
        DictRefs refs = getRefsUnionForConfig(Config.Hk2S, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Taiwan Traditional variants.
     *
     * @param input the Traditional Chinese input
     * @return the text converted to Taiwan-style Traditional Chinese
     */
    public String t2tw(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.T2Tw, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Taiwan Traditional with phrases and variants.
     *
     * @param input the Traditional Chinese input
     * @return the converted Taiwan Traditional Chinese with phrases and variants
     */
    public String t2twp(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.T2Twp, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Taiwan Traditional Chinese to base Traditional Chinese.
     *
     * @param input the Taiwan Traditional input
     * @return the converted base Traditional Chinese text
     */
    public String tw2t(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.Tw2T, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Taiwan Traditional Chinese to base Traditional Chinese, including phrase reversal.
     *
     * @param input the Taiwan Traditional input
     * @return the fully reverted Traditional Chinese text
     */
    public String tw2tp(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.Tw2Tp, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Hong Kong Traditional variants.
     *
     * @param input the Traditional Chinese input
     * @return the converted text using HK Traditional variants
     */
    public String t2hk(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.T2Hk, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Hong Kong Traditional Chinese to base Traditional Chinese.
     *
     * @param input the HK Traditional Chinese input
     * @return the converted base Traditional Chinese text
     */
    public String hk2t(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.Hk2T, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Japanese Kanji variants.
     *
     * @param input the Traditional Chinese input
     * @return the text converted to Japanese-style Kanji variants
     */
    public String t2jp(String input) {
        DictRefs refs = getDictRefs("t2jp");
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    /**
     * Converts Japanese-style Kanji back to Traditional Chinese.
     *
     * @param input the Japanese Kanji-style Chinese input
     * @return the converted Traditional Chinese text
     */
    public String jp2t(String input) {
        DictRefs refs = getDictRefs("jp2t");
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    /**
     * Converts each character in the input text from Simplified Chinese to Traditional Chinese.
     *
     * <p>This method uses only the character-level dictionary (`st_characters`) and does not
     * apply phrase or context-based replacements.
     *
     * @param input the input text in Simplified Chinese
     * @return the text converted to Traditional Chinese, character by character
     */
    public static String st(String input) {
        return convertSegment(input, Collections.singletonList(DictionaryHolder.get().st_characters), 2); // maxLength = 2 for surrogate-paired characters
    }

    /**
     * Converts each character in the input text from Traditional Chinese to Simplified Chinese.
     *
     * <p>This method uses only the character-level dictionary (`ts_characters`) and does not
     * apply phrase or context-based replacements.
     *
     * @param input the input text in Traditional Chinese
     * @return the text converted to Simplified Chinese, character by character
     */
    public static String ts(String input) {
        return convertSegment(input, Collections.singletonList(DictionaryHolder.get().ts_characters), 2); // maxLength = 2 for surrogate-paired characters
    }

    /**
     * Attempts to detect whether the input text is written in Traditional or Simplified Chinese.
     *
     * <p>This method removes non-Chinese characters, then analyzes the first ~200 UTF-8 bytes
     * (about 60–70 characters) for mismatches between the input and its conversion to
     * Simplified and Traditional Chinese.
     *
     * <p>Return codes:
     * <ul>
     *   <li>0 – Undetermined or mixed/other content</li>
     *   <li>1 – Likely Traditional Chinese</li>
     *   <li>2 – Likely Simplified Chinese</li>
     * </ul>
     *
     * @param input the input text to check
     * @return an integer code representing the detected Chinese variant
     */
    public static int zhoCheck(String input) {
        if (input == null || input.isEmpty()) return 0;

        int scanLength = Math.min(input.length(), 500);

        String stripped = DictRefs.STRIP_REGEX.matcher(input.substring(0, scanLength)).replaceAll("");
        if (stripped.isEmpty()) return 0;

        // Take first 100 code points
        int[] codePoints = stripped.codePoints()
                .limit(100)
                .toArray();

        String slice = new String(codePoints, 0, codePoints.length);

        if (!slice.equals(ts(slice))) return 1;
        if (!slice.equals(st(slice))) return 2;
        return 0;
    }

    /**
     * Attempts to detect whether the input text is written in Traditional or Simplified Chinese,
     * using this {@code OpenCC} instance.
     *
     * <p>This instance method delegates to the static {@link #zhoCheck(String)} detection
     * logic but is provided for convenience when working with an {@code OpenCC} object.</p>
     *
     * <p>Detection process:</p>
     * <ul>
     *   <li>Non-Chinese characters are stripped from the first portion of the text.</li>
     *   <li>Analysis is limited to the first ~200 UTF-8 bytes (≈60–70 characters),
     *       or 100 Unicode code points.</li>
     *   <li>The substring is compared against its conversions to Simplified and
     *       Traditional Chinese.</li>
     * </ul>
     *
     * <p>Return codes:</p>
     * <ul>
     *   <li><b>0</b> – Undetermined, mixed, or non-Chinese content</li>
     *   <li><b>1</b> – Likely Traditional Chinese</li>
     *   <li><b>2</b> – Likely Simplified Chinese</li>
     * </ul>
     *
     * @param input the input text to check (maybe {@code null} or empty)
     * @return an integer code representing the detected Chinese variant
     * @see #zhoCheck(String)
     * @deprecated since 1.1.0 – {@code zhoCheck} is now a static method.
     * Use {@link #zhoCheck(String)} instead, or this method
     * (@link zhoCheckInstance(String)) for instance-based
     * compatibility.
     */
    @Deprecated
    public final int zhoCheckInstance(String input) {
        return OpenCC.zhoCheck(input);
    }

    /**
     * Legacy helper that performed direct char-to-char punctuation substitution.
     *
     * <p><b>Deprecated since 1.1.0:</b> Punctuation conversion is now handled by
     * dictionary-driven entries ({@link DictionaryMaxlength.DictEntry}) within the
     * shared {@link DictionaryMaxlength} and applied by the normal conversion pipeline
     * (e.g., union keys that include punctuation variants). This approach supports
     * multi-code-point tokens, surrogate pairs, and locale-specific variants that a
     * simple {@code Map<Character, Character>} cannot model.</p>
     *
     * <p>This method is retained only for backward compatibility and tests and is not
     * used by the current implementation. Prefer enabling punctuation in your conversion
     * flow (via configuration/union selection or a punctuation flag on {@code convert(...)}).</p>
     *
     * @param input the original string to process
     * @param map   a mapping from source punctuation characters to their target equivalents
     * @return a new string with punctuation characters translated
     * @deprecated Use the standard dictionary-based conversion pipeline with punctuation
     * enabled (see {@link DictionaryMaxlength} and the conversion methods that
     * accept a punctuation option).
     */
    @Deprecated
    private String translatePunctuation(String input, Map<Character, Character> map) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            sb.append(map.getOrDefault(c, c));
        }
        return sb.toString();
    }

}
