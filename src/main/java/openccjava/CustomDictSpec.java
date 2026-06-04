package openccjava;

import java.nio.file.Path;
import java.util.*;

/**
 * Describes one custom dictionary patch operation.
 */
public final class CustomDictSpec {
    public final DictSlot slot;
    public final List<Path> paths;
    public final Map<String, String> pairs;
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

    public static CustomDictSpec fromFile(DictSlot slot, Path path, CustomDictMode mode) {
        return new CustomDictSpec(
                slot,
                Collections.singletonList(Objects.requireNonNull(path, "path")),
                Collections.emptyMap(),
                mode
        );
    }

    public static CustomDictSpec fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        return new CustomDictSpec(
                slot,
                Objects.requireNonNull(paths, "paths"),
                Collections.emptyMap(),
                mode
        );
    }

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
