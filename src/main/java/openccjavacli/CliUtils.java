package openccjavacli;

import openccjava.*;

import java.io.File;
import java.util.*;

/**
 * Shared helpers for building OpenCC converters from command-line options.
 *
 * <p>This class keeps option infrastructure shared by several CLI commands in
 * one place. It supplies canonical conversion-config candidates and delegates
 * {@code --custom-dict} token parsing to the public
 * {@link CustomDictSpec#parse(String)} core API.</p>
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
     * <p>The caller owns the returned converter and must close it, preferably
     * with try-with-resources.</p>
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

        return new OpenCC(typedConfig, parseCustomDictSpecs(customDictSpecs));
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

        return dict.withCustomDicts(parseCustomDictSpecs(customDictSpecs));
    }

    private static List<CustomDictSpec> parseCustomDictSpecs(List<String> values) {
        List<CustomDictSpec> specs = new ArrayList<>(values.size());
        for (String value : values) {
            specs.add(CustomDictSpec.parse(value));
        }
        return specs;
    }

    /**
     * Validates that a CLI input path exists and is a regular file.
     *
     * @param input input file supplied by the user
     * @throws IllegalArgumentException if {@code input} is {@code null}, does not
     *                                  exist, or is not a regular file
     */
    static void validateInputFile(File input) {
        if (input == null) {
            throw new IllegalArgumentException("Input file must not be null");
        }

        if (!input.exists()) {
            throw new IllegalArgumentException("Input file not found: " + input);
        }

        if (!input.isFile()) {
            throw new IllegalArgumentException("Input path is not a file: " + input);
        }
    }
}
