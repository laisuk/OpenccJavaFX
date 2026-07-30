package openccjava;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Describes one custom dictionary patch operation.
 *
 * <p>Use {@link #parse(String)} for portable textual tokens in the form
 * {@code <slot>:<append|override>:<path>}. Use {@link #fromFile(DictSlot, Path,
 * CustomDictMode)} when the slot, path, and mode are already strongly typed.
 * Both methods construct a specification only; dictionary files are loaded
 * later by {@link DictionaryMaxlength}.</p>
 *
 * <p>A spec selects one {@link DictSlot}, a {@link CustomDictMode}, and custom
 * mappings supplied by one or more UTF-8 OpenCC dictionary text files or an
 * in-memory map. Files are parsed with the same parser used for built-in
 * OpenCC text dictionaries: one entry per line, source, a tab, then target
 * text; blank lines and comment lines follow the existing parser behavior,
 * and only the first target token is used.</p>
 *
 * <p>Instances are immutable. The {@link #paths} list and {@link #pairs} map
 * are defensively copied and exposed as unmodifiable collections.</p>
 *
 * @see DictionaryMaxlength#fromDicts(java.util.List)
 * @see DictionaryMaxlength#withCustomDicts(java.util.List)
 */
public final class CustomDictSpec {
    private static final String EXPECTED_FORMAT = "<slot>:<append|override>:<path>";

    /**
     * The dictionary slot that this spec patches.
     */
    public final DictSlot slot;

    /**
     * UTF-8 OpenCC dictionary text files to apply, in order.
     *
     * <p>Files are parsed with the same parser used for built-in OpenCC
     * dictionaries. Later files win when the same source key appears more than
     * once within the same spec.</p>
     */
    public final List<Path> paths;

    /**
     * In-memory custom dictionary pairs to apply.
     *
     * <p>Each map entry represents one OpenCC dictionary mapping from source text
     * to target text. These pairs are applied after {@link #paths}, so pair
     * entries override file-loaded entries when the same source key exists in
     * both.</p>
     *
     * <p>The map is immutable and preserves insertion order.</p>
     */
    public final Map<String, String> pairs;

    /**
     * How the custom dictionary data is applied to the selected slot.
     *
     * <p>{@link CustomDictMode#Append} merges custom entries into the existing
     * dictionary slot, while {@link CustomDictMode#Override} replaces the slot
     * contents before applying custom entries.</p>
     */
    public final CustomDictMode mode;

    /**
     * Creates an immutable custom dictionary specification from file paths,
     * in-memory pairs, or both.
     *
     * @param slot  dictionary slot to modify
     * @param paths custom dictionary file paths
     * @param pairs in-memory dictionary pairs, or {@code null} for none
     * @param mode  append or override mode
     * @throws NullPointerException     if {@code slot}, {@code paths}, or {@code mode} is {@code null}
     * @throws IllegalArgumentException if both {@code paths} and {@code pairs} are empty
     */
    private CustomDictSpec(
            DictSlot slot,
            List<Path> paths,
            Map<String, String> pairs,
            CustomDictMode mode) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.paths = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(paths, "paths")
        ));
        this.pairs = Collections.unmodifiableMap(new LinkedHashMap<>(
                pairs == null ? Collections.emptyMap() : pairs
        ));
        this.mode = Objects.requireNonNull(mode, "mode");

        if (this.paths.isEmpty() && this.pairs.isEmpty()) {
            throw new IllegalArgumentException("paths or pairs must not be empty");
        }
    }

    /**
     * Parses a portable custom-dictionary specification.
     *
     * <p>The required grammar is
     * {@code <slot>:<append|override>:<path>}. Slot and mode matching is
     * case-insensitive. The value is split into at most three fields so Windows
     * drive-letter paths and other host-valid paths containing additional
     * colons are preserved. The path text must still be accepted by the host
     * platform's {@link java.nio.file.FileSystem}.</p>
     *
     * <p>This method validates textual syntax only. It does not test whether the
     * path exists, is readable, or identifies a regular file. File loading is
     * performed when the returned specification is applied through
     * {@link DictionaryMaxlength#fromDicts(java.util.List)} or
     * {@link DictionaryMaxlength#withCustomDicts(java.util.List)}.</p>
     *
     * @param value portable custom-dictionary token to parse
     * @return an immutable, single-file custom dictionary specification
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank,
     *                                  malformed, contains an unsupported slot or
     *                                  mode, or has an empty path
     */
    public static CustomDictSpec parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Custom dictionary specification cannot be null or empty"
            );
        }

        String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid custom dictionary specification '" + value
                            + "'. Expected: " + EXPECTED_FORMAT
            );
        }

        CustomDictMode parsedMode = getParsedMode(parts);

        return fromFile(
                DictSlot.parse(parts[0]),
                Paths.get(parts[2].trim()),
                parsedMode
        );
    }

    /**
     * Parses the custom dictionary mode token from the split specification.
     *
     * @param parts specification parts containing the mode at index {@code 1}
     * @return the parsed custom dictionary mode
     * @throws IllegalArgumentException if the mode is not {@code append} or {@code override}
     */
    private static CustomDictMode getParsedMode(String[] parts) {
        CustomDictMode parsedMode;
        String modeToken = parts[1].trim();
        if ("append".equalsIgnoreCase(modeToken)) {
            parsedMode = CustomDictMode.Append;
        } else if ("override".equalsIgnoreCase(modeToken)) {
            parsedMode = CustomDictMode.Override;
        } else {
            throw new IllegalArgumentException(
                    "Unknown custom dictionary mode '" + parts[1]
                            + "'. Valid values: append, override"
            );
        }
        return parsedMode;
    }

    /**
     * Creates a spec for one custom dictionary file.
     *
     * <p>This factory validates the typed values but does not test whether the
     * file exists. The file is opened only when the spec is passed to
     * {@link DictionaryMaxlength#fromDicts(java.util.List)},
     * {@link DictionaryMaxlength#fromDicts(String, java.util.List)}, or
     * {@link DictionaryMaxlength#withCustomDicts(java.util.List)}.</p>
     *
     * @param slot the dictionary slot to patch; must not be {@code null}
     * @param path the UTF-8 OpenCC dictionary text file; must not be {@code null}
     * @param mode append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException     if {@code slot}, {@code path}, or {@code mode} is {@code null}
     * @throws IllegalArgumentException if {@code slot} is inactive or {@code path} is empty
     */
    public static CustomDictSpec fromFile(DictSlot slot, Path path, CustomDictMode mode) {
        validateSlotAndMode(slot, mode);
        Path validatedPath = validatePath(path);
        return new CustomDictSpec(
                slot,
                Collections.singletonList(validatedPath),
                Collections.emptyMap(),
                mode
        );
    }

    /**
     * Creates a spec for multiple custom dictionary files applied in order.
     *
     * <p>All files are parsed with the same parser used for built-in OpenCC
     * text dictionaries. Later files win when they define the same source key
     * as earlier files in the same spec. The input list is defensively copied.
     * This factory does not test whether any file exists.</p>
     *
     * @param slot  the dictionary slot to patch; must not be {@code null}
     * @param paths UTF-8 OpenCC dictionary text files; must not be {@code null}
     *              or empty
     * @param mode  append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException     if {@code slot}, {@code paths}, any path element,
     *                                  or {@code mode} is {@code null}
     * @throws IllegalArgumentException if {@code slot} is inactive, {@code paths} is empty,
     *                                  or a path is empty
     */
    public static CustomDictSpec fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        validateSlotAndMode(slot, mode);
        Objects.requireNonNull(paths, "paths");
        List<Path> validatedPaths = new ArrayList<>(paths.size());
        for (Path path : paths) {
            validatedPaths.add(validatePath(path));
        }

        return new CustomDictSpec(
                slot,
                validatedPaths,
                Collections.emptyMap(),
                mode
        );
    }

    /**
     * Creates a spec for in-memory custom dictionary pairs.
     *
     * <p>Each map entry represents one OpenCC dictionary mapping from source text
     * to target text. The input map is defensively copied and stored as an
     * immutable map preserving insertion order.</p>
     *
     * <p>This factory is useful when custom dictionary entries are generated at
     * runtime or already exist in memory and do not need to be loaded from UTF-8
     * dictionary text files.</p>
     *
     * @param slot  the dictionary slot to patch; must not be {@code null}
     * @param pairs custom dictionary mappings; must not be {@code null} or empty
     * @param mode  append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException     if {@code slot}, {@code pairs}, or {@code mode}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code slot} is inactive or {@code pairs} is empty
     */
    public static CustomDictSpec fromPairs(
            DictSlot slot,
            Map<String, String> pairs,
            CustomDictMode mode
    ) {
        validateSlotAndMode(slot, mode);
        return new CustomDictSpec(
                slot,
                Collections.emptyList(),
                Objects.requireNonNull(pairs, "pairs"),
                mode
        );
    }

    /**
     * Validates that the dictionary slot and mode are supported.
     *
     * @param slot dictionary slot to validate
     * @param mode custom dictionary mode to validate
     * @throws NullPointerException     if {@code slot} or {@code mode} is {@code null}
     * @throws IllegalArgumentException if the slot is inactive or the mode is unsupported
     */
    private static void validateSlotAndMode(
            DictSlot slot,
            CustomDictMode mode) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(mode, "mode");

        if (!slot.isActive()) {
            throw new IllegalArgumentException(
                    "Unknown or retired dictionary slot: " + slot
            );
        }

        if (mode != CustomDictMode.Append && mode != CustomDictMode.Override) {
            throw new IllegalArgumentException(
                    "Unknown custom dictionary mode: " + mode
            );
        }
    }

    /**
     * Validates a custom dictionary path and removes surrounding whitespace
     * from its textual representation.
     *
     * @param path path to validate
     * @return a path created from the trimmed path text
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if the path text is empty
     */
    private static Path validatePath(Path path) {
        Objects.requireNonNull(path, "path");
        String pathText = path.toString().trim();
        if (pathText.isEmpty()) {
            throw new IllegalArgumentException("Custom dictionary path cannot be empty");
        }
        return Paths.get(pathText);
    }
}
