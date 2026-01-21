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
     * @return the default OpenCC configuration ({@link #S2T})
     */
    public static OpenccConfig defaultConfig() {
        return S2T;
    }

    /**
     * Returns the canonical lowercase name of this configuration.
     *
     * <p>The canonical name is the standard, lowercase identifier used by
     * OpenCC for configuration keys (for example {@code "s2t"},
     * {@code "s2twp"}, {@code "tw2sp"}).</p>
     *
     * @return the canonical lowercase configuration name
     */
    public String toCanonicalName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Lookup table for fast, case-insensitive configuration parsing.
     *
     * <p>This map serves as the internal canonical index from string representations
     * of OpenCC configuration names to their corresponding {@link OpenccConfig}
     * enum constants.</p>
     */
    private static final Map<String, OpenccConfig> LOOKUP = buildLookup();

    /**
     * Builds the internal lookup table for configuration string parsing.
     *
     * @return an immutable map from normalized configuration strings to
     * {@link OpenccConfig} values
     */
    private static Map<String, OpenccConfig> buildLookup() {
        Map<String, OpenccConfig> m = new HashMap<>();
        for (OpenccConfig c : values()) {
            m.put(c.name().toLowerCase(Locale.ROOT), c);
            m.put(c.toCanonicalName(), c);
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
     * <p>This method never throws and is suitable for tolerant input handling.</p>
     *
     * @param value the configuration string to parse; may be {@code null}
     * @return the corresponding {@link OpenccConfig}, or {@code null} if invalid
     */
    public static OpenccConfig tryParse(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return LOOKUP.get(trimmed.toLowerCase(Locale.ROOT));
    }

    /**
     * Converts a configuration string to its canonical lowercase name,
     * or returns {@code null} if the input is invalid.
     *
     * @param value the configuration string to canonicalize; may be {@code null}
     * @return the canonical lowercase configuration name, or {@code null}
     */
    public static String toCanonicalNameOrNull(String value) {
        OpenccConfig c = tryParse(value);
        return c == null ? null : c.toCanonicalName();
    }

    /**
     * Immutable list of all supported canonical OpenCC configuration names.
     */
    private static final List<String> SUPPORTED_CANONICAL_NAMES =
            Collections.unmodifiableList(buildSupportedCanonicalNames());

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
     * @return an unmodifiable list of supported canonical configuration names
     */
    public static List<String> supportedCanonicalNames() {
        return SUPPORTED_CANONICAL_NAMES;
    }

    /**
     * Checks whether a configuration string is a valid OpenCC conversion configuration.
     *
     * <p>The check is case-insensitive and accepts both canonical names
     * (for example {@code "s2t"}, {@code "t2twp"}) and enum-style names
     * (for example {@code "S2T"}, {@code "T2TWP"}).</p>
     *
     * <p>This method performs no allocation beyond parsing and never throws.</p>
     *
     * @param value the configuration string to check; may be {@code null}
     * @return {@code true} if the configuration is valid; {@code false} otherwise
     */
    public static boolean isValidConfig(String value) {
        return tryParse(value) != null;
    }
}
