package openccjava;

import java.nio.file.Path;
import java.util.*;

/**
 * Describes one custom dictionary patch operation.
 *
 * <p>A spec selects one {@link DictSlot}, one or more UTF-8 OpenCC dictionary
 * text files, and a {@link CustomDictMode}. The files are parsed with the same
 * parser used for built-in OpenCC text dictionaries: one entry per line,
 * source, a tab, then target text; blank lines and comment lines follow the
 * existing parser behavior, and only the first target token is used.</p>
 *
 * <p>Instances are immutable. The {@link #paths} list is defensively copied and
 * exposed as an unmodifiable list.</p>
 *
 * @see DictionaryMaxlength#fromDicts(java.util.List)
 * @see DictionaryMaxlength#withCustomDictFiles(java.util.List)
 */
public final class CustomDictSpec {
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

    private CustomDictSpec(DictSlot slot, List<Path> paths, Map<String, String> pairs, CustomDictMode mode) {
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
     * Creates a spec for one custom dictionary file.
     *
     * <p>The file is parsed later, when the spec is passed to
     * {@link DictionaryMaxlength#fromDicts(java.util.List)},
     * {@link DictionaryMaxlength#fromDicts(String, java.util.List)}, or
     * {@link DictionaryMaxlength#withCustomDictFiles(java.util.List)}.</p>
     *
     * @param slot the dictionary slot to patch; must not be {@code null}
     * @param path the UTF-8 OpenCC dictionary text file; must not be {@code null}
     * @param mode append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException if {@code slot}, {@code path}, or {@code mode} is {@code null}
     */
    public static CustomDictSpec fromFile(DictSlot slot, Path path, CustomDictMode mode) {
        return new CustomDictSpec(
                slot,
                Collections.singletonList(Objects.requireNonNull(path, "path")),
                Collections.emptyMap(),
                mode
        );
    }

    /**
     * Creates a spec for multiple custom dictionary files applied in order.
     *
     * <p>All files are parsed with the same parser used for built-in OpenCC
     * text dictionaries. Later files win when they define the same source key
     * as earlier files in the same spec. The input list is defensively copied.</p>
     *
     * @param slot  the dictionary slot to patch; must not be {@code null}
     * @param paths UTF-8 OpenCC dictionary text files; must not be {@code null}
     *              or empty
     * @param mode  append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException     if {@code slot}, {@code paths}, or {@code mode} is {@code null}
     * @throws IllegalArgumentException if {@code paths} is empty
     */
    public static CustomDictSpec fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        return new CustomDictSpec(
                slot,
                Objects.requireNonNull(paths, "paths"),
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
     * @throws IllegalArgumentException if {@code pairs} is empty
     */
    public static CustomDictSpec fromPairs(
            DictSlot slot,
            Map<String, String> pairs,
            CustomDictMode mode
    ) {
        return new CustomDictSpec(
                slot,
                Collections.emptyList(),
                Objects.requireNonNull(pairs, "pairs"),
                mode
        );
    }
}
