package openccjavacli;

import openccjava.*;

import java.nio.file.Paths;
import java.util.*;

/**
 * Shared helpers for building OpenCC converters from command-line options.
 *
 * <p>This class keeps option infrastructure shared by several CLI commands in
 * one place. It supplies canonical conversion-config candidates and translates
 * {@code --custom-dict} values from the CLI format
 * {@code slot:append|override:path} into {@link CustomDictSpec}
 * instances.</p>
 *
 * <p>The helpers are intentionally package-private because they are part of the
 * CLI implementation rather than the public OpenCC Java API.</p>
 */
public final class CliUtils {
    /**
     * Utility class; not instantiable.
     */
    private CliUtils() {
    }


    /**
     * Supplies canonical OpenCC configuration names for CLI option completion
     * and generated help text.
     */
    @SuppressWarnings("NullableProblems")
    static final class ConfigCandidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return OpenCC.getSupportedConfigs().iterator();
        }
    }

    /**
     * Creates an {@link OpenCC} instance for a CLI command.
     *
     * <p>If {@code config} is not recognized, the library default config is
     * used. When no custom dictionary specs are supplied, the converter uses the
     * shared built-in dictionaries for the selected config. Otherwise, each
     * {@code --custom-dict} value is parsed and passed to {@link OpenCC}, which
     * creates a customized copy of the shared dictionary without modifying the
     * singleton dictionary.</p>
     *
     * @param config          CLI config name, such as {@code s2t}, {@code t2s},
     *                        or {@code null} to use the default config
     * @param customDictSpecs custom dictionary specs in
     *                        {@code slot:append|override:path} form; may be
     *                        {@code null} or empty
     * @return an OpenCC converter configured for the command
     * @throws IllegalArgumentException if any custom dictionary spec is invalid
     * @throws RuntimeException         if a custom dictionary file cannot be loaded
     */
    static OpenCC createOpenCC(
            String config,
            List<String> customDictSpecs
    ) {
        OpenccConfig typedConfig = OpenccConfig.tryParse(config);
        if (typedConfig == null) {
            typedConfig = OpenccConfig.defaultConfig();
        }

        if (customDictSpecs == null || customDictSpecs.isEmpty()) {
            return new OpenCC(typedConfig);
        }

        List<CustomDictSpec> specs = new ArrayList<>();
        for (String raw : customDictSpecs) {
            specs.add(parseCustomDictSpec(raw));
        }

        return new OpenCC(typedConfig, specs);
    }

    /**
     * Applies CLI custom dictionary specifications to an existing dictionary.
     *
     * <p>When no custom dictionary specs are supplied, the original dictionary is
     * returned unchanged. Otherwise, each {@code --custom-dict} value is parsed
     * and applied to the supplied dictionary, producing a customized copy while
     * leaving the original dictionary unmodified.</p>
     *
     * @param dict            base dictionary to customize
     * @param customDictSpecs custom dictionary specs in
     *                        {@code slot:append|override:path} form; may be
     *                        {@code null} or empty
     * @return the original dictionary if no custom dictionary specs are supplied;
     * otherwise a customized copy with the requested custom dictionaries applied
     * @throws IllegalArgumentException if any custom dictionary spec is invalid
     * @throws RuntimeException         if a custom dictionary file cannot be loaded
     */
    static DictionaryMaxlength applyCustomDictionary(
            DictionaryMaxlength dict,
            List<String> customDictSpecs
    ) {
        if (customDictSpecs == null || customDictSpecs.isEmpty()) {
            return dict;
        }

        List<CustomDictSpec> specs = new ArrayList<>();
        for (String raw : customDictSpecs) {
            specs.add(parseCustomDictSpec(raw));
        }

        return dict.withCustomDictFiles(specs);
    }

    /**
     * Parses one {@code --custom-dict} option value.
     *
     * <p>The expected format is {@code slot:append|override:path}. The slot name
     * is matched by {@link #parseDictSlot(String)}, the mode by
     * {@link #parseCustomDictMode(String)}, and the path is kept as the remaining
     * third field so platform paths containing additional colon characters are
     * preserved.</p>
     *
     * @param raw raw CLI option value
     * @return a custom dictionary spec backed by the supplied file path
     * @throws IllegalArgumentException if {@code raw} is {@code null}, blank, or
     *                                  not in {@code slot:mode:path} form, or if
     *                                  the slot or mode is invalid
     */
    static CustomDictSpec parseCustomDictSpec(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty --custom-dict spec");
        }

        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid --custom-dict spec: " + raw +
                            " (expected slot:append|override:path)"
            );
        }

        DictSlot slot = parseDictSlot(parts[0]);
        CustomDictMode mode = parseCustomDictMode(parts[1]);
        return CustomDictSpec.fromFile(slot, Paths.get(parts[2]), mode);
    }

    private static final Map<String, DictSlot> SLOT_LOOKUP = createSlotLookup();

    /**
     * Builds the normalized lookup table used by {@link #parseDictSlot(String)}.
     *
     * @return an immutable map from CLI-friendly slot names to dictionary slots
     */
    private static Map<String, DictSlot> createSlotLookup() {
        Map<String, DictSlot> map = new HashMap<>();

        for (DictSlot slot : DictSlot.values()) {
            map.put(normalize(slot.name()), slot);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Normalizes a slot token for forgiving command-line matching.
     *
     * <p>Users may type dictionary slots with different case, hyphens, or
     * underscores, for example {@code hk-phrases-rev},
     * {@code HK_Phrases_Rev}, or {@code hkphrasesrev}.</p>
     *
     * @param value slot token to normalize
     * @return normalized slot token
     */
    private static String normalize(String value) {
        return value
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a custom dictionary slot name.
     *
     * <p>Matching ignores case, hyphens, and underscores so CLI users do not
     * have to type enum names exactly.</p>
     *
     * @param value dictionary slot token from the command line
     * @return the matching dictionary slot
     * @throws IllegalArgumentException if {@code value} does not name a known
     *                                  {@link DictSlot}
     */
    static DictSlot parseDictSlot(String value) {
        DictSlot slot = SLOT_LOOKUP.get(normalize(value));
        if (slot == null) {
            throw new IllegalArgumentException("Invalid custom dict slot: " + value);
        }
        return slot;
    }

    /**
     * Parses a custom dictionary application mode.
     *
     * @param value mode token from the command line; must be {@code append} or
     *              {@code override}, ignoring case
     * @return the matching custom dictionary mode
     * @throws IllegalArgumentException if {@code value} is not {@code append} or
     *                                  {@code override}
     */
    static CustomDictMode parseCustomDictMode(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if ("append".equals(v)) return CustomDictMode.Append;
        if ("override".equals(v)) return CustomDictMode.Override;
        throw new IllegalArgumentException("Invalid custom dict mode: " + value);
    }
}
