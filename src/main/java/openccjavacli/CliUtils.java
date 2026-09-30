package openccjavacli;

import openccjava.*;

import java.io.File;
import java.io.IOException;
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
     * Supplies canonical OpenCC configuration names for CLI option completion
     * and generated help text.
     */
    @SuppressWarnings("NullableProblems")
    static final class SlotCandidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return DictSlot.supportedCanonicalNames().iterator();
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
     * Creates the shared CLI text-conversion pipeline used by text and Office
     * conversion commands.
     *
     * <p>The returned converter applies transformations in this order:</p>
     * <ol>
     *     <li>Extended compatibility normalization when requested, otherwise
     *     CJK compatibility normalization when requested.</li>
     *     <li>OpenCC conversion, optionally including punctuation conversion.</li>
     *     <li>DeToFu fallback replacement when requested.</li>
     * </ol>
     *
     * <p>When both normalization flags are enabled, extended normalization takes
     * precedence because it already includes CJK Compatibility Ideograph
     * normalization.</p>
     *
     * <p>The DeToFu level is parsed once when this method is called rather than
     * once for every Office text fragment. A checked {@link IOException} raised
     * while loading a custom DeToFu file is wrapped in an
     * {@link IllegalStateException}, because {@link TextConverter} is a
     * general text transformation contract and does not expose checked
     * exceptions.</p>
     *
     * @param opencc             OpenCC converter used by the pipeline
     * @param punctuation        whether OpenCC punctuation conversion is enabled
     * @param normCompat         whether CJK compatibility normalization is enabled
     * @param normCompatExtended whether extended Unicode compatibility
     *                           normalization is enabled; takes precedence over
     *                           {@code normCompat}
     * @param detofu             DeToFu level name, or {@code null}/blank to
     *                           disable DeToFu
     * @param detofuFile         optional custom DeToFu mapping file; requires an
     *                           enabled {@code detofu} level
     * @return a reusable text converter implementing the requested CLI pipeline
     * @throws IllegalArgumentException if the DeToFu options are inconsistent or
     *                                  the level name is invalid
     */
    static TextConverter createTextConverter(
            OpenCC opencc,
            boolean punctuation,
            boolean normCompat,
            boolean normCompatExtended,
            String detofu,
            File detofuFile
    ) {
        if (opencc == null) {
            throw new IllegalArgumentException("OpenCC converter must not be null");
        }

        validateDeTofuOptions(detofu, detofuFile);

        final DeTofu.Level detofuLevel =
                detofu == null || detofu.trim().isEmpty()
                        ? null
                        : DeTofu.Level.parse(detofu);

        return text -> {
            String result = text;

            if (normCompatExtended) {
                result = opencc.normalizeCompatExtended(result);
            } else if (normCompat) {
                result = opencc.normalizeCompat(result);
            }

            result = opencc.convert(result, punctuation);
            if (result == null) {
                throw new IllegalStateException(
                        "OpenCC conversion failed: " + opencc.getLastError()
                );
            }

            if (detofuLevel != null) {
                if (detofuFile != null) {
                    try {
                        result = opencc.deTofuWithCustomFile(
                                result,
                                detofuLevel,
                                detofuFile.getPath()
                        );
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Failed to load DeToFu custom file: " + detofuFile,
                                e
                        );
                    }
                } else {
                    result = opencc.deTofu(result, detofuLevel);
                }
            }

            return result;
        };
    }

    /**
     * Validates the relationship between the DeToFu CLI options.
     *
     * @param detofu     DeToFu level name, or {@code null}/blank when disabled
     * @param detofuFile optional custom DeToFu mapping file
     * @throws IllegalArgumentException if {@code detofuFile} is supplied without
     *                                  enabling {@code detofu}
     */
    static void validateDeTofuOptions(String detofu, File detofuFile) {
        if (detofuFile != null && (detofu == null || detofu.trim().isEmpty())) {
            throw new IllegalArgumentException("--detofu-file requires --detofu");
        }
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
            CustomDictSpec spec = CustomDictSpec.parse(value);

            for (java.nio.file.Path path : spec.paths) {
                validateFile(path.toFile(), "Custom dictionary file");
            }

            specs.add(spec);
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
        validateFile(input, "Input file");
    }

    private static void validateFile(File file, String label) {
        if (file == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }

        if (!file.exists()) {
            throw new IllegalArgumentException(label + " not found: " + file);
        }

        if (!file.isFile()) {
            throw new IllegalArgumentException(label + " is not a file: " + file);
        }
    }
}
