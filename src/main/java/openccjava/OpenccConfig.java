package openccjava;

import java.util.*;

/**
 * Supported conversion configurations.
 *
 * <p>Each constant corresponds to an OpenCC conversion mode,
 * covering Simplified ↔ Traditional Chinese and region-specific variants
 * (Taiwan, Hong Kong, Japan). The {@code p} suffix indicates that
 * phrase-level mappings are also applied.</p>
 */
public enum OpenccConfig {

    /**
     * Simplified → Traditional.
     */
    S2T,

    /**
     * Traditional → Simplified.
     */
    T2S,

    /**
     * Simplified → Traditional (Taiwan).
     */
    S2Tw,

    /**
     * Traditional (Taiwan) → Simplified.
     */
    Tw2S,

    /**
     * Simplified → Traditional (Taiwan, with phrases).
     */
    S2Twp,

    /**
     * Traditional (Taiwan, with phrases) → Simplified.
     */
    Tw2Sp,

    /**
     * Simplified → Traditional (Hong Kong).
     */
    S2Hk,

    /**
     * Traditional (Hong Kong) → Simplified.
     */
    Hk2S,

    /**
     * Traditional → Traditional (Taiwan).
     */
    T2Tw,

    /**
     * Traditional → Traditional (Taiwan, with phrases).
     */
    T2Twp,

    /**
     * Traditional (Taiwan) → Traditional.
     */
    Tw2T,

    /**
     * Traditional (Taiwan, with phrases) → Traditional.
     */
    Tw2Tp,

    /**
     * Traditional → Traditional (Hong Kong).
     */
    T2Hk,

    /**
     * Traditional (Hong Kong) → Traditional.
     */
    Hk2T,

    /**
     * Traditional → Japanese Shinjitai.
     */
    T2Jp,

    /**
     * Japanese Shinjitai → Traditional.
     */
    Jp2T;

    /**
     * Returns the default OpenCC configuration.
     *
     * <p>This method defines the single authoritative default configuration
     * used by the Java binding whenever no configuration is explicitly provided
     * or when an invalid configuration is encountered during tolerant parsing.</p>
     *
     * <p>Centralizing the default here ensures consistent behavior across
     * CLI tools, GUI applications, and library consumers, and avoids duplicated
     * fallback logic in higher-level APIs.</p>
     *
     * @return the default OpenCC configuration ({@link #S2T})
     */
    public static OpenccConfig defaultConfig() {
        return S2T;
    }

    /**
     * Returns the lowercase string form of this config.
     * <p>
     * Example: {@code S2T.asStr()} → {@code "s2t"}.
     * </p>
     *
     * @return the lowercase string representation
     */
    public String asStr() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a string into a corresponding {@code Config} constant, ignoring case.
     *
     * <p>This method performs a case-insensitive lookup of the provided value
     * against all available configuration constants. It supports both upper-
     * and lower-case forms, including mixed-case variants such as
     * {@code "s2twp"} or {@code "T2Twp"}.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * Config c1 = Config.fromStr("s2t");    // returns Config.S2T
     * Config c2 = Config.fromStr("T2Twp");  // returns Config.T2Twp
     * Config c3 = Config.fromStr("tw2tp");  // returns Config.Tw2Tp
     * }</pre>
     *
     * @param value the configuration key string (e.g., {@code "s2t"}, {@code "t2s"},
     *              {@code "t2twp"}, {@code "tw2tp"})
     * @return the matching {@code Config} constant
     * @throws IllegalArgumentException if {@code value} is {@code null} or does not
     *                                  correspond to any known configuration constant
     */
    public static OpenccConfig fromStr(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Config string cannot be null");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Config string cannot be empty");
        }

        for (OpenccConfig c : OpenccConfig.values()) {
            if (c.name().equalsIgnoreCase(trimmed)
                    || c.asStr().equalsIgnoreCase(trimmed)) {
                return c;
            }
        }

        throw new IllegalArgumentException("Unknown config: " + value);
    }

    /**
     * Lookup table for fast, case-insensitive configuration parsing.
     *
     * <p>This map serves as the internal canonical index from string representations
     * of OpenCC configuration names to their corresponding {@link OpenccConfig}
     * enum constants.</p>
     *
     * <p>The following input forms are supported:</p>
     * <ul>
     *   <li>Enum-style names (for example {@code "S2T"}, {@code "T2TWP"})</li>
     *   <li>Canonical lowercase names (for example {@code "s2t"}, {@code "t2twp"})</li>
     * </ul>
     *
     * <p>All keys are normalized to lowercase using {@link java.util.Locale#ROOT}
     * to ensure locale-independent behavior.</p>
     *
     * <p>This lookup table is immutable and is intended for internal use by
     * tolerant parsing helpers such as {@link #tryParse(String)}. Public APIs
     * should rely on these helpers rather than accessing this map directly.</p>
     */
    private static final Map<String, OpenccConfig> LOOKUP = buildLookup();

    /**
     * Builds the internal lookup table for configuration string parsing.
     *
     * <p>Each {@link OpenccConfig} constant contributes one or more normalized
     * string keys to the map, allowing both enum-style names and canonical
     * OpenCC configuration names to resolve to the same enum value.</p>
     *
     * <p>The resulting map is wrapped using
     * {@link java.util.Collections#unmodifiableMap(Map)} to prevent accidental
     * modification and to guarantee thread-safe, read-only access.</p>
     *
     * @return an immutable map from normalized configuration strings to
     * {@link OpenccConfig} values
     */
    private static Map<String, OpenccConfig> buildLookup() {
        Map<String, OpenccConfig> m = new HashMap<>();
        for (OpenccConfig c : values()) {
            m.put(c.name().toLowerCase(Locale.ROOT), c);  // enum-style normalized
            m.put(c.toCanonicalName(), c);                // canonical OpenCC name
        }
        return Collections.unmodifiableMap(m);
    }

    /**
     * Attempts to parse a configuration string into a corresponding
     * {@link OpenccConfig} value.
     *
     * <p>The parsing is case-insensitive and accepts both canonical
     * lowercase names (for example {@code "s2t"}, {@code "t2twp"}) and
     * enum-style names (for example {@code "S2T"}, {@code "T2TWP"}).</p>
     *
     * <p>This method performs no allocation beyond parsing and never throws.
     * It is intended for tolerant input handling such as CLI arguments,
     * UI bindings, or configuration files.</p>
     *
     * @param value the configuration string to parse; may be {@code null}
     * @return the corresponding {@link OpenccConfig}, or {@code null} if
     * the input is {@code null}, empty, or does not match any
     * supported configuration
     */
    public static OpenccConfig tryParse(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return LOOKUP.get(trimmed.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the canonical lowercase name of this configuration.
     *
     * <p>The canonical name is the standard, lowercase identifier used by
     * OpenCC for configuration keys (for example {@code "s2t"},
     * {@code "s2twp"}, {@code "tw2sp"}).</p>
     *
     * <p>This method is functionally equivalent to {@link #asStr()}, but is
     * named explicitly for clarity when used in public APIs, CLI tools, and
     * configuration serialization.</p>
     *
     * @return the canonical lowercase configuration name
     */
    public String toCanonicalName() {
        return asStr();
    }

    /**
     * Converts a configuration string to its canonical lowercase name.
     *
     * <p>The input string is resolved in a case-insensitive manner against
     * all supported {@link OpenccConfig} values. Both enum-style names
     * (for example {@code "S2T"}, {@code "T2TWP"}) and canonical OpenCC
     * names (for example {@code "s2t"}, {@code "t2twp"}) are accepted.</p>
     *
     * <p>This method is <b>strict</b>: if the input does not correspond to any
     * supported configuration, an {@link IllegalArgumentException} is thrown.
     * For tolerant input handling, use {@link #toCanonicalNameOrNull(String)}.</p>
     *
     * @param value the configuration string to canonicalize
     * @return the canonical lowercase OpenCC configuration name
     * @throws IllegalArgumentException if {@code value} is {@code null},
     *                                  empty, or does not correspond to any
     *                                  supported configuration
     */
    public static String toCanonicalName(String value) {
        return fromStr(value).toCanonicalName();
    }

    /**
     * Converts a configuration string to its canonical lowercase name,
     * or returns {@code null} if the input is invalid.
     *
     * <p>The input is parsed in a case-insensitive manner. This method never
     * throws and is suitable for tolerant input handling.</p>
     *
     * @param value the configuration string to canonicalize; may be {@code null}
     * @return the canonical lowercase configuration name, or {@code null} if
     * the input is {@code null}, empty, or invalid
     */
    public static String toCanonicalNameOrNull(String value) {
        OpenccConfig c = tryParse(value);
        return c == null ? null : c.asStr();
    }

    /**
     * Immutable list of all supported canonical OpenCC configuration names.
     *
     * <p>This list is derived directly from {@link OpenccConfig#values()}
     * and therefore reflects the complete and authoritative set of
     * configurations supported by this enum.</p>
     *
     * <p>The values are returned in their canonical lowercase form
     * (for example {@code "s2t"}, {@code "t2twp"}, {@code "tw2sp"}), suitable
     * for use in native calls, CLI help output, configuration files,
     * and serialization.</p>
     *
     * <p>The list is unmodifiable and safe for concurrent access.</p>
     */
    private static final List<String> SUPPORTED_CANONICAL_NAMES =
            Collections.unmodifiableList(buildSupportedCanonicalNames());

    /**
     * Builds the list of canonical configuration names supported by this enum.
     *
     * <p>Each {@link OpenccConfig} constant contributes exactly one canonical
     * name to the resulting list. The order of the list follows the
     * declaration order of the enum constants.</p>
     *
     * <p>This method is used during class initialization and is not part of
     * the public API.</p>
     *
     * @return a mutable list containing all canonical configuration names
     */
    private static List<String> buildSupportedCanonicalNames() {
        OpenccConfig[] all = values();
        List<String> out = new ArrayList<>(all.length);
        for (OpenccConfig c : all) {
            out.add(c.toCanonicalName());
        }
        return out;
    }

    /**
     * Returns an immutable list of all supported canonical OpenCC configuration names.
     *
     * <p>The returned list contains the canonical lowercase identifiers used by
     * OpenCC (for example {@code "s2t"}, {@code "s2twp"}, {@code "tw2sp"}).</p>
     *
     * <p>This method provides a programmatic way to query supported configurations
     * for use in CLI argument validation, UI selection lists, documentation
     * generation, or configuration tooling.</p>
     *
     * <p>The returned list is immutable and reflects the enum definition exactly.
     * No defensive copying is performed.</p>
     *
     * @return an unmodifiable list of supported canonical configuration names
     */
    public static List<String> supportedCanonicalNames() {
        return SUPPORTED_CANONICAL_NAMES;
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
        return tryParse(value) != null;
    }

}
