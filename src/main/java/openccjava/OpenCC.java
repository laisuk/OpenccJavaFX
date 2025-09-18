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
     * Loaded dictionary data containing phrase/character maps and their max lengths.
     */
    private final DictionaryMaxlength dictionary;
    private final ConversionPlanCache planCache;

    public enum Config {
        S2T, T2S, S2Tw, Tw2S, S2Twp, Tw2Sp,
        S2Hk, Hk2S, T2Tw, T2Twp, Tw2T, Tw2Tp,
        T2Hk, Hk2T, T2Jp, Jp2T;

        /**
         * Return lowercase string form (e.g. S2T -> "s2t").
         */
        public String asStr() {
            return name().toLowerCase();
        }

        /**
         * Parse string (case-insensitive).
         *
         * @param value string like "s2t", "T2S", etc.
         * @return Config enum constant
         * @throws IllegalArgumentException if no match
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
    private final Set<Character> delimiters = DictRefs.DELIMITERS;

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
     * Constructs an OpenCC instance using the default configuration ("s2t").
     */
    public OpenCC() {
        this("s2t");
    }

    /**
     * Constructs an OpenCC instance with a specified conversion configuration.
     * <p>
     * This constructor attempts to load the dictionary data in the following order:
     * </p>
     * <ol>
     *   <li><b>File system:</b> Attempts to load {@code dicts/dictionary_maxlength.json} from the current working directory.</li>
     *   <li><b>Classpath resource:</b> If not found on the file system, attempts to load the same JSON from the application's resources (e.g. {@code /dicts/dictionary_maxlength.json} inside the JAR).</li>
     *   <li><b>Plain text fallback:</b> If the JSON is not found in either location, falls back to loading individual dictionary text files from {@code dicts/} using {@link DictionaryMaxlength#fromDicts()}.</li>
     * </ol>
     *  <p><i>Informational log messages</i> are emitted when a dictionary is successfully loaded from a specific source.</p>
     *  <p><i>Warnings</i> are logged if the loader falls back to text-based dictionaries.</p>
     *
     * @param config the OpenCC conversion configuration key (e.g. "s2t", "tw2sp")
     * @throws RuntimeException if any dictionary source fails to load or parse
     */
    public OpenCC(String config) {
        DictionaryMaxlength loaded;
        try {
            Path jsonPath = Paths.get("dicts", "dictionary_maxlength.json");

            if (Files.exists(jsonPath)) {
                loaded = DictionaryMaxlength.fromJson(jsonPath.toString());
                LOGGER.info("Loaded dictionary from file system.");
            } else {
                try (InputStream in = DictionaryMaxlength.class.getResourceAsStream("/dicts/dictionary_maxlength.json")) {
                    if (in != null) {
                        loaded = DictionaryMaxlength.fromJson(in);
                        LOGGER.info("Loaded dictionary from embedded resource.");
                    } else {
                        loaded = DictionaryMaxlength.fromDicts();
                        LOGGER.warning("Falling back to plain text dictionaries.");
                    }
                }
            }

        } catch (Exception e) {
            this.lastError = e.getMessage();
            throw new RuntimeException("Failed to load dictionaries", e);
        }

        this.dictionary = loaded;
        this.planCache = new ConversionPlanCache(() -> this.dictionary);

        setConfig(config);
    }

    // expose helper
    private DictRefs getRefsUnionForConfig(Config cfg, boolean punctuation) {
        return planCache.getPlan(cfg, punctuation);
    }

    public void clearPlanCache() {
        planCache.clear();
    }

    /**
     * Constructs an OpenCC instance using plain text dictionaries from the given directory.
     * Skips JSON and directly loads .txt files (e.g., for user-customized dictionaries).
     *
     * @param config   the conversion configuration key to use
     * @param dictPath the path to the text dictionary directory
     */
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

        final DictionaryMaxlength d = this.dictionary; // no 'var' in Java 8
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

        if (useParallel) {
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String segment = text.substring(range[0], range[1]);
                segments[i] = convertSegment(segment, dicts, maxLength);
            });

            // Join all converted segments
            StringBuilder sb = new StringBuilder(text.length());
            for (String seg : segments) {
                sb.append(seg);
            }

            return sb.toString();
        } else {
            // Fallback: sequential processing
            StringBuilder sb = new StringBuilder(text.length());
            for (int[] range : ranges) {
                String segment = text.substring(range[0], range[1]);
                sb.append(convertSegment(segment, dicts, maxLength));
            }
            return sb.toString();
        }
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
    public String convertSegment(String segment, List<DictEntry> dicts, int maxLength) {
        if (segment.length() == 1 && delimiters.contains(segment.charAt(0))) {
            return segment;
        }

        int segLen = segment.length();
        StringBuilder sb = threadLocalSb.get();
        sb.setLength(0); // reset for reuse

        int i = 0;
        while (i < segLen) {
            int bestLen = 0;
            String bestMatch = null;
            int maxScanLen = Math.min(maxLength, segLen - i);

            for (int len = maxScanLen; len > 0; len--) {
                final int end = i + len;
                String word = segment.substring(i, end);
                for (DictEntry entry : dicts) {
                    if (entry.maxLength < len) continue;

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
    public List<int[]> getSplitRanges(String text, boolean inclusive) {
        List<int[]> result = new ArrayList<>();
        int start = 0;
        final int textLength = text.length(); // Cache text length

        for (int i = 0; i < textLength; i++) {
            if (delimiters.contains(text.charAt(i))) {
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
    private static final class DictPartition {
        final List<DictionaryMaxlength.DictEntry> phraseDicts;
        final List<DictionaryMaxlength.DictEntry> singleDicts;
        final int phraseMaxLen;

        DictPartition(List<DictionaryMaxlength.DictEntry> phraseDicts,
                      List<DictionaryMaxlength.DictEntry> singleDicts,
                      int phraseMaxLen) {
            this.phraseDicts = phraseDicts;
            this.singleDicts = singleDicts;
            this.phraseMaxLen = phraseMaxLen;
        }
    }

    private static DictPartition partitionDicts(List<DictionaryMaxlength.DictEntry> dicts) {
        List<DictionaryMaxlength.DictEntry> phrase = new ArrayList<>(dicts.size());
        List<DictionaryMaxlength.DictEntry> single = new ArrayList<>(2);
        int maxLen = 0;

        for (DictionaryMaxlength.DictEntry e : dicts) {
            if (e.maxLength >= 3) {
                phrase.add(e);
                if (e.maxLength > maxLen) maxLen = e.maxLength;
            } else {
                single.add(e);
            }
        }

        return new DictPartition(
                Collections.unmodifiableList(phrase),
                Collections.unmodifiableList(single),
                maxLen
        );
    }

    /**
     * Faster bridge using union + phrase/single partition.
     */
    // New: union-aware segmentReplace (keeps your original splitting & parallel flow)
    public String segmentReplaceWithUnion(String text,
                                          List<DictionaryMaxlength.DictEntry> dicts,
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

        boolean useParallel = text.length() > 10_000 || numSegments > 100;

        if (useParallel) {
            String[] segments = new String[numSegments];
            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String seg = text.substring(range[0], range[1]);
                segments[i] = convertSegmentWithUnion(seg, part, maxLength, union);
            });
            StringBuilder sb = new StringBuilder(text.length());
            for (String seg : segments) sb.append(seg);
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(text.length());
            for (int[] range : ranges) {
                String seg = text.substring(range[0], range[1]);
                sb.append(convertSegmentWithUnion(seg, part, maxLength, union));
            }
            return sb.toString();
        }
    }

    // Internal: faster converter for a single segment using pre-partitioned dicts
    private String convertSegmentWithUnion(String input,
                                           DictPartition part,
                                           int roundMaxLen,
                                           StarterUnion union) {
        if (input.isEmpty()) return input;

        final int n = input.length();
        final StringBuilder out = new StringBuilder(n + (n >> 3));

        for (int i = 0; i < n; ) {
            final int cp = input.codePointAt(i);
            final int starterLen = Character.charCount(cp);

            if (union != null && !union.hasStarter(cp)) {
                out.appendCodePoint(cp);
                i += starterLen;
                continue;
            }

            String hit = null;
            int hitLen = 0;

            // phrase search
            if (!part.phraseDicts.isEmpty()) {
                final int remaining = n - i;
                final int tryMax = Math.min(Math.min(part.phraseMaxLen, roundMaxLen), remaining);
                if (tryMax >= 3) {
                    outer:
                    for (int len = tryMax; len >= 3; len--) {
                        final String sub = input.substring(i, i + len);
                        for (DictionaryMaxlength.DictEntry e : part.phraseDicts) {
                            if (e.maxLength < len) continue;
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

            // single-char search
            if (hit == null && !part.singleDicts.isEmpty() && i + starterLen <= n) {
                final String sub = input.substring(i, i + starterLen);
                for (DictionaryMaxlength.DictEntry e : part.singleDicts) {
                    final String repl = e.dict.get(sub);
                    if (repl != null) {
                        hit = repl;
                        hitLen = starterLen;
                        break;
                    }
                }
            }

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
        DictRefs refs = getRefsUnionForConfig(Config.T2Jp, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Japanese-style Kanji back to Traditional Chinese.
     *
     * @param input the Japanese Kanji-style Chinese input
     * @return the converted Traditional Chinese text
     */
    public String jp2t(String input) {
        DictRefs refs = getRefsUnionForConfig(Config.Jp2T, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
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
    public String st(String input) {
        return convertSegment(input, Collections.singletonList(dictionary.st_characters), 2); // maxLength = 2 for surrogate-paired characters
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
    public String ts(String input) {
        return convertSegment(input, Collections.singletonList(dictionary.ts_characters), 2); // maxLength = 2 for surrogate-paired characters
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
    public int zhoCheck(String input) {
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
     * Translates punctuation characters in the input string using the provided character map.
     *
     * <p>This method performs a simple character-by-character substitution based on the map.
     * It is typically used to convert punctuation between fullwidth Simplified/Traditional Chinese styles
     * (e.g., 「」『』 ↔ “ ” ‘ ’).
     *
     * @param input the original string to process
     * @param map   a mapping from source punctuation characters to their target equivalents
     * @return a new string with punctuation characters translated
     */
    private String translatePunctuation(String input, Map<Character, Character> map) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            sb.append(map.getOrDefault(c, c));
        }
        return sb.toString();
    }

}
