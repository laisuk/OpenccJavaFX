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
     * Global logger for diagnostic and fallback messages.
     *
     * <p>This logger is shared across all {@link OpenCC} instances and is used to report
     * non-critical events such as dictionary loading, resource fallbacks, or
     * configuration details. Logging is <strong>disabled by default</strong> to prevent
     * unwanted console output in GUI or end-user environments.</p>
     *
     * <p>To enable logging for debugging or integration testing, call
     * {@link #setVerboseLogging(boolean)} with {@code true}.</p>
     */
    static final Logger LOGGER = Logger.getLogger(OpenCC.class.getName());

    static {
        // Disable logging by default to keep console output clean
        LOGGER.setLevel(Level.OFF);
    }

    /**
     * Enables or disables verbose logging for OpenCC.
     *
     * <p>When enabled, the global {@link #LOGGER} will emit informational messages
     * related to dictionary loading, resource fallbacks, and runtime diagnostics.
     * When disabled (the default), all log output is suppressed.</p>
     *
     * <p>This setting affects all {@link OpenCC} instances in the current JVM.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * OpenCC.setVerboseLogging(true);
     * OpenCC converter = new OpenCC("s2t");
     * converter.convert("汉字"); // Logs diagnostic info
     * }</pre>
     *
     * @param enabled {@code true} to enable verbose logging; {@code false} to disable it
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
     * Cached DictRefs to avoid redundant config resolution.
     */
    private final EnumMap<OpenccConfig, DictRefs> configCacheById =
            new EnumMap<>(OpenccConfig.class);

    /**
     * Stores the last error message encountered, if any.
     */
    private String lastError;

    /**
     * Default conversion configuration for OpenCC
     */
    private static final OpenccConfig DEFAULT_CONFIG = OpenccConfig.defaultConfig();

    /**
     * Backing field for default config
     */
    private OpenccConfig configId = DEFAULT_CONFIG;   // ✅ single source of truth

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

    // ---------- Static helpers ----------

    /**
     * Creates a new {@link OpenCC} instance from a configuration string.
     *
     * <p>The provided {@code config} string is parsed in a case-insensitive manner
     * using {@link OpenccConfig#tryParse(String)}. If the string does not correspond
     * to any supported configuration, the default configuration
     * ({@code s2t}) is used instead.</p>
     *
     * <p>This method never throws due to an invalid configuration string.
     * The effective configuration can be queried via {@link #getConfig()}
     * or {@link #getConfigId()}.</p>
     *
     * @param config the configuration key (e.g. {@code "s2t"}, {@code "S2TWP"}, {@code "tw2s"});
     *               may be {@code null}
     * @return a new {@link OpenCC} instance using the parsed configuration,
     * or the default configuration if parsing fails
     * @since 1.1.0
     */
    public static OpenCC fromConfig(String config) {
        return new OpenCC(OpenccConfig.tryParse(config));
    }

    /**
     * Creates a new {@link OpenCC} instance with the specified configuration ID.
     *
     * <p>If {@code configId} is {@code null}, the default configuration
     * ({@code s2t}) is used.</p>
     *
     * @param configId the configuration ID, or {@code null} to use the default
     * @return a new {@link OpenCC} instance using the specified (or defaulted) configuration
     * @since 1.1.0
     */
    public static OpenCC fromConfig(OpenccConfig configId) {
        return new OpenCC(configId);
    }

    // ---------- Instance constructors + API ----------

    /**
     * Constructs an {@code OpenCC} instance using the default configuration
     * ({@code s2t}).
     *
     * <p>This is equivalent to {@code new OpenCC(OpenccConfig.S2T)}.</p>
     *
     * @since 1.0.0
     */
    public OpenCC() {
        this(OpenccConfig.S2T);
    }

    /**
     * Constructs an {@code OpenCC} instance using a configuration string.
     *
     * <p>The provided {@code config} string is parsed in a case-insensitive manner
     * via {@link OpenccConfig#tryParse(String)}. If the string is {@code null},
     * empty, or does not correspond to any supported configuration, the default
     * configuration ({@code s2t}) is used.</p>
     *
     * <p>This constructor does <em>not</em> reload dictionaries for each instance.
     * Instead, it retrieves a shared singleton {@link DictionaryMaxlength}
     * from {@link DictionaryHolder}. The dictionary is loaded lazily on first access
     * and reused by all subsequent {@code OpenCC} instances.</p>
     *
     * <p>The shared dictionary is resolved in the following order
     * (performed only once per JVM):</p>
     *
     * <ol>
     *   <li>
     *     <b>JSON file from the file system:</b><br>
     *     {@code dicts/dictionary_maxlength.json} in the current working directory.
     *   </li>
     *   <li>
     *     <b>Embedded JSON resource:</b><br>
     *     {@code /dicts/dictionary_maxlength.json} available on the application
     *     classpath (for example, packaged inside the JAR).
     *   </li>
     *   <li>
     *     <b>Plain-text fallback:</b><br>
     *     If neither JSON source is found, individual dictionary text files are
     *     loaded from {@code dicts/} using
     *     {@link DictionaryMaxlength#fromDicts()}.
     *   </li>
     * </ol>
     *
     * <p><b>Important:</b> Because the dictionary is a shared singleton,
     * any modification to its contents (for example, adding or removing entries)
     * will affect <em>all</em> {@code OpenCC} instances within the same JVM.</p>
     *
     * <p>If all dictionary loading attempts fail, a {@link RuntimeException}
     * is thrown. The underlying failure reason is recorded in
     * {@link #getLastError()}.</p>
     *
     * @param config the configuration key (for example {@code "s2t"},
     *               {@code "S2TWP"}, {@code "tw2sp"}); may be {@code null}
     * @throws RuntimeException if no dictionary source can be loaded or parsed
     * @since 1.1.0
     */
    public OpenCC(String config) {
        this(OpenccConfig.tryParse(config));
    }

    /**
     * Constructs an {@code OpenCC} instance using a typed configuration ID.
     *
     * <p>If {@code configId} is {@code null}, the default configuration
     * ({@code s2t}) is used.</p>
     *
     * <p>The underlying dictionary is obtained from {@link DictionaryHolder}
     * and shared across all {@code OpenCC} instances. Dictionary loading
     * is performed lazily on first access.</p>
     *
     * @param configId the configuration ID, or {@code null} to use the default
     * @throws RuntimeException if no dictionary source can be loaded or parsed
     * @since 1.1.0
     */
    public OpenCC(OpenccConfig configId) {
        this.dictionary = DictionaryHolder.get(); // Lazy static, loaded on first access
        setConfig(configId);
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
        try {
            this.dictionary = DictionaryMaxlength.fromDicts(dictPath.toString());
        } catch (Exception e) {
            this.lastError = e.getMessage();
            throw new RuntimeException("Failed to load text dictionaries from: " + dictPath, e);
        }

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
    private DictRefs getDictRefsUnionForConfigId(OpenccConfig cfg, boolean punctuation) {
        return dictionary.getPlan(cfg, punctuation);
    }

    /**
     * Sets the current conversion configuration using a configuration string.
     *
     * <p>The provided {@code config} is parsed in a case-insensitive manner.
     * If {@code config} is {@code null}, empty, or invalid, the default
     * configuration ({@code s2t}) is applied and {@link #getLastError()} is set.</p>
     *
     * <p>On success, {@link #getLastError()} is cleared.</p>
     *
     * @param config configuration key (e.g. {@code "s2t"}, {@code "S2TWP"}); may be {@code null}
     */
    public void setConfig(String config) {
        setConfig(OpenccConfig.tryParse(config));
    }

    /**
     * Sets the current conversion configuration using a typed configuration ID.
     *
     * <p>If {@code cfg} is {@code null}, the default configuration ({@code s2t})
     * is applied and {@link #getLastError()} is set.</p>
     *
     * <p>On success, {@link #getLastError()} is cleared.</p>
     *
     * @param cfg configuration ID, or {@code null} to use default
     */
    public void setConfig(OpenccConfig cfg) {
        if (cfg != null) {
            this.configId = cfg;
            this.lastError = null;
        } else {
            this.configId = DEFAULT_CONFIG;
            this.lastError = "Invalid config: null. Using default '" + DEFAULT_CONFIG.toCanonicalName() + "'.";
        }
    }

    /**
     * Returns the current conversion configuration in canonical string form.
     *
     * <p>The returned value is the lowercase, canonical configuration name
     * (for example {@code "s2t"}, {@code "t2twp"}, {@code "tw2sp"}).
     * This method never returns {@code null}.</p>
     *
     * <p>This method is provided for compatibility with string-based APIs
     * (such as CLI options, configuration files, and legacy integrations).
     * For type-safe access, prefer {@link #getConfigId()}.</p>
     *
     * @return the canonical configuration key representing the current conversion mode
     */
    public String getConfig() {
        return configId.toCanonicalName();
    }

    /**
     * Returns the current conversion configuration as a typed enum ID.
     *
     * <p>This is the authoritative, type-safe representation of the active
     * configuration and serves as the single source of truth for all internal
     * conversion logic.</p>
     *
     * <p>The returned value is never {@code null}. If an invalid configuration
     * was previously provided, this method returns the default configuration
     * ({@code s2t}).</p>
     *
     * @return the active {@link OpenccConfig} identifier
     */
    public OpenccConfig getConfigId() {
        return configId;
    }

    /**
     * Checks whether a configuration string corresponds to a supported
     * OpenCC conversion configuration.
     *
     * <p>The check is case-insensitive and accepts both canonical names
     * (for example {@code "s2t"}, {@code "t2twp"}) and enum-style names
     * (for example {@code "S2T"}, {@code "T2TWP"}).</p>
     *
     * <p>This method performs no allocation beyond parsing and never throws.</p>
     *
     * @param value the configuration string to check; may be {@code null}
     * @return {@code true} if the configuration is supported; {@code false} otherwise
     */
    public static boolean isSupportedConfig(String value) {
        return OpenccConfig.isValidConfig(value);
    }

    /**
     * Returns an immutable list of all supported configuration keys
     * in canonical string form.
     *
     * <p>The returned list contains lowercase configuration names such as
     * {@code "s2t"}, {@code "tw2sp"}, {@code "t2twp"}.
     * The order of the list matches the declaration order of
     * {@link OpenccConfig}.</p>
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return an unmodifiable list of supported configuration keys
     */
    public static List<String> getSupportedConfigs() {
        ArrayList<String> out = new ArrayList<String>();
        for (OpenccConfig c : OpenccConfig.values()) out.add(c.toCanonicalName());
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns an immutable list of all supported configuration IDs.
     *
     * <p>This method exposes the full set of {@link OpenccConfig} enum constants
     * and is intended for type-safe usage such as UI bindings, configuration
     * selectors, or programmatic inspection.</p>
     *
     * <p>The returned list is backed by the enum values and should be treated
     * as read-only.</p>
     *
     * @return an unmodifiable list of supported {@link OpenccConfig} identifiers
     */
    public static List<OpenccConfig> getSupportedConfigIds() {
        return Collections.unmodifiableList(Arrays.asList(OpenccConfig.values()));
    }

    /**
     * Returns the most recent non-fatal error message recorded by this instance.
     *
     * <p>The {@code lastError} value represents the most recent recoverable
     * issue encountered during configuration changes or other operations
     * that did not throw an exception. Typical examples include:</p>
     *
     * <ul>
     *   <li>Providing an invalid or unsupported configuration string</li>
     *   <li>Falling back to the default configuration due to invalid input</li>
     * </ul>
     *
     * <p>{@code lastError} does <em>not</em> indicate a failed conversion,
     * nor does it imply that the {@code OpenCC} instance is in an invalid state.
     * All conversions proceed using a valid configuration.</p>
     *
     * <p>The value is cleared automatically when a subsequent operation
     * completes successfully (for example, a valid call to {@code setConfig(...)}
     * with {@code setLastError = true}), or manually via
     * {@link #clearLastError()}.</p>
     *
     * @return the most recent error message, or {@code null} if no error
     * has been recorded
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Clears the currently stored non-fatal error message, if any.
     *
     * <p>This method has no effect on the active configuration or conversion
     * behavior. It simply resets the error state used for diagnostic or
     * user-facing reporting.</p>
     *
     * <p>This method is typically used by UI or CLI code after an error
     * has been displayed to the user.</p>
     */
    public void clearLastError() {
        this.lastError = null;
    }

    /**
     * Returns whether a non-fatal error message is currently recorded.
     *
     * <p>This is a convenience method equivalent to
     * {@code getLastError() != null}.</p>
     *
     * <p>A return value of {@code true} does <em>not</em> imply that the
     * last conversion failed or that the instance is unusable; it only
     * indicates that a recoverable issue was recorded.</p>
     *
     * @return {@code true} if an error message is present; {@code false} otherwise
     */
    public boolean hasLastError() {
        return this.lastError != null;
    }

    /**
     * Converts the given input text using the current conversion configuration.
     *
     * <p>This is a convenience overload equivalent to calling
     * {@link #convert(String, boolean)} with {@code punctuation} set to {@code false}.
     * Only character and phrase conversion is applied; punctuation characters
     * are left unchanged.</p>
     *
     * <p>If the input is {@code null} or empty, no conversion is performed.
     * In that case, an explanatory message is recorded in {@link #getLastError()}
     * and returned as the result.</p>
     *
     * @param input the text to convert; may be {@code null}
     * @return the converted text, or an error message if the input is invalid
     */
    public String convert(String input) {
        return convert(input, false); // default punctuation = false
    }

    /**
     * Converts the given input text using the current conversion configuration.
     *
     * <p>The conversion behavior is determined by the active configuration
     * ({@link #getConfigId()}), such as Simplified → Traditional ({@code s2t}),
     * Traditional → Simplified ({@code t2s}), or region-specific variants
     * (Taiwan, Hong Kong, Japan).</p>
     *
     * <p>If {@code punctuation} is {@code true}, punctuation characters are
     * converted using the corresponding punctuation dictionaries where applicable.
     * If {@code false}, punctuation is preserved as-is.</p>
     *
     * <p>This method never throws due to an invalid configuration.
     * The active configuration is always valid; if an invalid configuration
     * was previously supplied, the default configuration ({@code s2t})
     * is used instead.</p>
     *
     * <p>If {@code input} is {@code null} or empty, no conversion is performed.
     * An explanatory message is recorded in {@link #getLastError()} and returned
     * as the result.</p>
     *
     * <p>The returned string always reflects the conversion result of a valid
     * configuration or a human-readable error message for invalid input.
     * A recorded error does <em>not</em> indicate that the {@code OpenCC}
     * instance is unusable.</p>
     *
     * @param input       the text to convert; may be {@code null}
     * @param punctuation whether to convert punctuation characters in addition
     *                    to text conversion
     * @return the converted text, or an error message if the input is invalid
     */
    public String convert(String input, boolean punctuation) {
        if (input == null || input.isEmpty()) {
            lastError = "Input text is null or empty";
            return lastError;
        }

        String result;
        switch (configId) {
            case S2T:
                result = s2t(input, punctuation);
                break;
            case T2S:
                result = t2s(input, punctuation);
                break;
            case S2Tw:
                result = s2tw(input, punctuation);
                break;
            case Tw2S:
                result = tw2s(input, punctuation);
                break;
            case S2Twp:
                result = s2twp(input, punctuation);
                break;
            case Tw2Sp:
                result = tw2sp(input, punctuation);
                break;
            case S2Hk:
                result = s2hk(input, punctuation);
                break;
            case Hk2S:
                result = hk2s(input, punctuation);
                break;

            case T2Tw:
                result = t2tw(input);
                break;
            case T2Twp:
                result = t2twp(input);
                break;
            case Tw2T:
                result = tw2t(input);
                break;
            case Tw2Tp:
                result = tw2tp(input);
                break;
            case T2Hk:
                result = t2hk(input);
                break;
            case Hk2T:
                result = hk2t(input);
                break;

            case T2Jp:
                result = t2jp(input);
                break;
            case Jp2T:
                result = jp2t(input);
                break;

            default:
                // Should be unreachable unless new enum values are added
                // without updating this switch.
                lastError = "Unsupported config id: " + configId;
                result = lastError;
                break;
        }

        return result;
    }

    /**
     * Retrieves the {@link DictRefs} for a given conversion configuration ID.
     *
     * <p>This method checks the internal cache first. If no entry is found,
     * it creates a new {@code DictRefs} object using the relevant dictionary entries
     * from {@link DictionaryMaxlength}, supporting up to 3 rounds of replacements.
     *
     * <p>The result is cached for future lookups to avoid recomputation.
     *
     * @param cfg the configuration ID
     * @return a {@code DictRefs} instance representing the translation rules,
     * or {@code null} if {@code cfg} is unsupported (should be rare)
     */
    private DictRefs getDictRefs(OpenccConfig cfg) {
        if (cfg == null) return null;

        DictRefs cached = configCacheById.get(cfg);
        if (cached != null) return cached;

        final DictionaryMaxlength d = dictionary; // Java 8
        DictRefs refs;

        switch (cfg) {
            // -----------------------------
            // Simplified <-> Traditional
            // -----------------------------
            case S2T:
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters));
                break;

            case T2S:
                refs = new DictRefs(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            // -----------------------------
            // Simplified <-> Taiwan
            // -----------------------------
            case S2Tw:
                // S -> T, then apply TW variants
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.tw_variants));
                break;

            case Tw2S:
                // TW -> T (reverse variants), then T -> S
                refs = new DictRefs(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            case S2Twp:
                // S -> T, then TW phrases, then TW variants
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.tw_phrases))
                        .withRound3(Collections.singletonList(d.tw_variants));
                break;

            case Tw2Sp:
                // TW phrases reverse + TW variants reverse, then T -> S
                refs = new DictRefs(Arrays.asList(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            // -----------------------------
            // Simplified <-> Hong Kong
            // -----------------------------
            case S2Hk:
                // S -> T, then HK variants
                refs = new DictRefs(Arrays.asList(d.st_phrases, d.st_characters))
                        .withRound2(Collections.singletonList(d.hk_variants));
                break;

            case Hk2S:
                // HK -> T (reverse variants), then T -> S
                refs = new DictRefs(Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev))
                        .withRound2(Arrays.asList(d.ts_phrases, d.ts_characters));
                break;

            // -----------------------------
            // Traditional <-> Taiwan (Traditional region transform)
            // -----------------------------
            case T2Tw:
                // T -> TW (variants only)
                refs = new DictRefs(Collections.singletonList(d.tw_variants));
                break;

            case T2Twp:
                // T -> TW (phrases + variants)
                refs = new DictRefs(Collections.singletonList(d.tw_phrases))
                        .withRound2(Collections.singletonList(d.tw_variants));
                break;

            case Tw2T:
                // TW -> T (reverse variants)
                refs = new DictRefs(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev));
                break;

            case Tw2Tp:
                // TW (variants-rev pair) -> T, then apply TW phrases/idioms reverse last
                refs = new DictRefs(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev))
                        .withRound2(Collections.singletonList(d.tw_phrases_rev));
                break;

            // -----------------------------
            // Traditional <-> Hong Kong (Traditional region transform)
            // -----------------------------
            case T2Hk:
                // T -> HK (variants only)
                refs = new DictRefs(Collections.singletonList(d.hk_variants));
                break;

            case Hk2T:
                // HK -> T (reverse variants)
                refs = new DictRefs(Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev));
                break;

            // -----------------------------
            // Japanese Shinjitai
            // -----------------------------
            case T2Jp:
                // T -> JP variants
                refs = new DictRefs(Collections.singletonList(d.jp_variants));
                break;

            case Jp2T:
                // JP -> T (jps phrases/chars + reverse variants)
                refs = new DictRefs(Arrays.asList(d.jps_phrases, d.jps_characters, d.jp_variants_rev));
                break;

            default:
                // Should never happen if switch is complete for the enum
                refs = null;
                break;
        }

        if (refs != null) configCacheById.put(cfg, refs);
        return refs;
    }

    /**
     * Wrapper for legacy string keys (e.g., "s2t", "tw2sp").
     *
     * <p>This method canonicalizes and delegates to {@link #getDictRefs(OpenccConfig)}.
     *
     * @param key the configuration key (case-insensitive)
     * @return a {@code DictRefs} instance or {@code null} if unsupported
     */
    private DictRefs getDictRefs(String key) {
        if (key == null) return null;
        OpenccConfig cfg = OpenccConfig.tryParse(key);
        if (cfg == null) return null;
        return getDictRefs(cfg);
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
        int textLength = text.length();

        // Fast path: entire text is one uninterrupted segment
        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == textLength) {
            return convertSegment(text, dicts, maxLength);
        }

        // Use parallel stream if input is large or highly segmented
        boolean useParallel = textLength > 10_000 || numSegments > 100;
        int sbCapacity = textLength + (textLength >> 4);
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
        int textLen = text.length();

        List<int[]> ranges = getSplitRanges(text, true);
        int numSegments = ranges.size();

        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == textLen) {
            return convertSegmentWithUnion(text, part, maxLength, union);
        }

        boolean useParallel = textLen > 100_000 || numSegments > 1_000;
        int sbCapacity = textLen + (textLen >> 4);
        StringBuilder sb = new StringBuilder(sbCapacity);

        if (useParallel) {
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String seg = text.substring(range[0], range[1]);
                segments[i] = convertSegmentWithUnion(seg, part, maxLength, union);
            });

            for (String seg : segments) sb.append(seg);
        } else {
            for (int[] range : ranges) {
                String segment = text.substring(range[0], range[1]);
                sb.append(convertSegmentWithUnion(segment, part, maxLength, union));
            }
        }
        return sb.toString();
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.S2T, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.T2S, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.S2Tw, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Tw2S, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.S2Twp, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Tw2Sp, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.S2Hk, punctuation);
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
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Hk2S, punctuation);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Taiwan Traditional variants.
     *
     * @param input the Traditional Chinese input
     * @return the text converted to Taiwan-style Traditional Chinese
     */
    public String t2tw(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.T2Tw, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Taiwan Traditional with phrases and variants.
     *
     * @param input the Traditional Chinese input
     * @return the converted Taiwan Traditional Chinese with phrases and variants
     */
    public String t2twp(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.T2Twp, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Taiwan Traditional Chinese to base Traditional Chinese.
     *
     * @param input the Taiwan Traditional input
     * @return the converted base Traditional Chinese text
     */
    public String tw2t(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Tw2T, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Taiwan Traditional Chinese to base Traditional Chinese, including phrase reversal.
     *
     * @param input the Taiwan Traditional input
     * @return the fully reverted Traditional Chinese text
     */
    public String tw2tp(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Tw2Tp, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Traditional Chinese to Hong Kong Traditional variants.
     *
     * @param input the Traditional Chinese input
     * @return the converted text using HK Traditional variants
     */
    public String t2hk(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.T2Hk, false);
        return refs.applySegmentReplace(input, this::segmentReplaceWithUnion);
    }

    /**
     * Converts Hong Kong Traditional Chinese to base Traditional Chinese.
     *
     * @param input the HK Traditional Chinese input
     * @return the converted base Traditional Chinese text
     */
    public String hk2t(String input) {
        DictRefs refs = getDictRefsUnionForConfigId(OpenccConfig.Hk2T, false);
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
     * @link zhoCheckInstance(String) for instance-based
     * compatibility.
     * @see #zhoCheck(String)
     * @deprecated since 1.1.0 – {@code zhoCheck} is now a static method.
     * Use {@link #zhoCheck(String)} instead, or this method
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
