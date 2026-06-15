package openccjava;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Represents a container for all OpenCC dictionary mappings,
 * including phrase-level and character-level dictionaries.
 *
 * <p>This class supports loading from:</p>
 * <ul>
 *     <li>JSON-serialized form (used in deployments)</li>
 *     <li>Individual dictionary text files (used during development or as fallback)</li>
 * </ul>
 *
 * <p>Each dictionary is stored as a {@link DictEntry} with:</p>
 * <ul>
 *     <li>{@code dict}: key-value pairs of source→target</li>
 *     <li>{@code maxLength}: the longest phrase/key length</li>
 *     <li>{@code minLength}: the shortest phrase/key length</li>
 * </ul>
 * Holds multiple dictionary entries, each with defined minimum and maximum key lengths.
 * Used for efficient longest-match text conversion.
 */
public class DictionaryMaxlength {
    /**
     * Constructs an empty {@code DictionaryMaxlength} instance.
     * All dictionary entries must be assigned manually.
     */
    public DictionaryMaxlength() {
        // No-op constructor for deserialization or manual setup
    }

    /**
     * Represents a dictionary entry with mapping data and max phrase length.
     */
//    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class DictEntry {
        /**
         * Key-value dictionary (e.g., "电脑" → "計算機")
         */
        public Map<String, String> dict;

        /**
         * Maximum phrase length in this dictionary
         */
        public int maxLength;

        /**
         * Minimum phrase length in this dictionary
         */
        public int minLength; // NEW

        /**
         * Constructs an empty dictionary entry.
         * <p>Kept for potential future serialization frameworks or manual instantiation.</p>
         */
        public DictEntry() {
            this.dict = new HashMap<>();
            this.maxLength = 0;
            this.minLength = 0;
        }

        /**
         * Constructs a new dictionary entry.
         *
         * @param dict      The dictionary mapping strings.
         * @param maxLength The maximum key length in the dictionary.
         * @param minLength The minimum key length in the dictionary.
         */
        public DictEntry(Map<String, String> dict, int maxLength, int minLength) {
            this.dict = Objects.requireNonNull(dict, "dict");
            this.maxLength = maxLength;
            this.minLength = minLength;
        }
    }

    // Dictionary fields (populated via JSON or fallback loading)
    // Simplified to Traditional
    /**
     * Simplified character mappings.
     */
    public DictEntry st_characters;
    /**
     * Simplified phrase mappings.
     */
    public DictEntry st_phrases;
    /**
     * Simplified punctuations.
     */
    public DictEntry st_punctuations;

    // Traditional to Simplified
    /**
     * Traditional character mappings.
     */
    public DictEntry ts_characters;
    /**
     * Traditional phrase mappings.
     */
    public DictEntry ts_phrases;
    /**
     * Traditional punctuations.
     */
    public DictEntry ts_punctuations;

    // Traditional to Taiwan
    /**
     * Taiwan phrase mappings.
     */
    public DictEntry tw_phrases;
    /**
     * Taiwan phrase reverse mappings.
     */
    public DictEntry tw_phrases_rev;
    /**
     * Taiwan variant mappings.
     */
    public DictEntry tw_variants;
    /**
     * Taiwan variant phrase mappings.
     */
    public DictEntry tw_variants_phrases;
    /**
     * Taiwan variant reverse mappings.
     */
    public DictEntry tw_variants_rev;
    /**
     * Taiwan variant reverse phrase mappings.
     */
    public DictEntry tw_variants_rev_phrases;

    // Hong Kong variants
    /**
     * Hong Kong phrase mappings.
     */
    public DictEntry hk_phrases;
    /**
     * Hong Kong phrase reverse mappings.
     */
    public DictEntry hk_phrases_rev;
    /**
     * Hong Kong variant mappings.
     */
    public DictEntry hk_variants;
    /**
     * Hong Kong variant phrase mappings.
     */
    public DictEntry hk_variants_phrases;
    /**
     * Hong Kong variant reverse mappings.
     */
    public DictEntry hk_variants_rev;
    /**
     * Hong Kong variant reverse phrase mappings.
     */
    public DictEntry hk_variants_rev_phrases;

    // Japanese Shinjitai
    /**
     * Japanese Shinjitai-to-Traditional Kyujitai character mappings.
     */
    public DictEntry jps_characters;
    /**
     * Traditional Kyujitai-to-Japanese Shinjitai character mappings.
     */
    public DictEntry jps_characters_rev;
    /**
     * Japanese Shinjitai-to-Traditional Kyujitai phrase mappings.
     */
    public DictEntry jps_phrases;

    /**
     * Returns a human-readable summary showing how many dictionaries are loaded.
     */
    @Override
    public String toString() {
        DictEntry[] entries = {
                st_characters, st_phrases, st_punctuations,
                ts_characters, ts_phrases, ts_punctuations,
                tw_phrases, tw_phrases_rev, tw_variants, tw_variants_phrases,
                tw_variants_rev, tw_variants_rev_phrases,
                hk_phrases, hk_phrases_rev, hk_variants, hk_variants_phrases,
                hk_variants_rev, hk_variants_rev_phrases,
                jps_characters, jps_characters_rev, jps_phrases
        };

        int count = 0;
        for (DictEntry entry : entries) {
            if (hasEntries(entry)) count++;
        }
        return "<DictionaryMaxlength with " + count + " loaded dicts>";
    }

    private static boolean hasEntries(DictEntry entry) {
        return entry != null && entry.dict != null && !entry.dict.isEmpty();
    }

    /**
     * Loads all dictionary files from the default "dicts" directory.
     *
     * @return the fully populated {@code DictionaryMaxlength} object
     */
    public static DictionaryMaxlength fromDicts() {
        return fromDicts("dicts");
    }

    /**
     * Loads all dictionary files from the specified base directory.
     *
     * @param basePath the path to the directory containing dictionary .txt files
     * @return the fully populated {@code DictionaryMaxlength} object
     */
    public static DictionaryMaxlength fromDicts(String basePath) {
        final DictionaryMaxlength r = new DictionaryMaxlength();
        final Map<String, String> files = getFiles();
        final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> assign = getAssign();

        for (Map.Entry<String, String> kv : files.entrySet()) {
            final String dictName = kv.getKey();
            final String filename = kv.getValue();
            final BiConsumer<DictionaryMaxlength, DictEntry> setter = assign.get(dictName);
            if (setter == null) {
                throw new IllegalStateException(
                        "Missing assignment mapping for dictionary '" + dictName +
                                "' (filename=" + filename + ")");
            }

            final Path fsPath = Paths.get(basePath, filename);
            try {
                final DictEntry entry;
                if (Files.exists(fsPath)) {
                    entry = loadDictionaryMaxlength(fsPath);
                } else {
                    final String resPath = "/" + basePath + "/" + filename;
                    try (InputStream in = DictionaryMaxlength.class.getResourceAsStream(resPath)) {
                        if (in == null) {
                            if (isOptionalTextDictionary(dictName)) {
                                // Direct HK phrase dictionaries were added after the original schema.
                                // Treat missing files as empty so older dictionary folders still load.
                                entry = new DictEntry();
                            } else {
                                throw new FileNotFoundException("Missing resource: " + resPath +
                                        " (also checked FS: " + fsPath.toAbsolutePath() + ")");
                            }
                        } else {
                            entry = loadDictionaryMaxlength(in);
                        }
                    }
                }

                setter.accept(r, entry);
            } catch (IOException ex) {
                throw new RuntimeException("Error loading dict: " + dictName + " (" + filename + ")", ex);
            }
        }

        ensureOptionalHkPhraseEntries(r);
        return r;
    }

    /**
     * Loads all dictionary files from the default {@code dicts} directory and
     * applies custom dictionary files to selected slots.
     *
     * <p>This is the file-level preload customization path. The official
     * dictionaries are loaded first, then each {@link CustomDictSpec} patches
     * one selected {@link DictSlot}. Custom dictionary files are parsed with the
     * same parser used for built-in OpenCC text dictionaries.</p>
     *
     * <p>This method creates and returns a caller-owned dictionary. It does not
     * read from or modify {@link OpenCC.DictionaryHolder}. If {@code customSpecs}
     * is {@code null} or empty, this behaves like {@link #fromDicts()}.</p>
     *
     * @param customSpecs custom dictionary specs to apply; may be {@code null}
     *                    or empty
     * @return a newly loaded {@code DictionaryMaxlength} with custom patches applied
     * @throws RuntimeException     if an official or custom dictionary file cannot be loaded
     * @throws NullPointerException if {@code customSpecs} contains a {@code null} spec
     */
    public static DictionaryMaxlength fromDicts(List<CustomDictSpec> customSpecs) {
        return fromDicts("dicts", customSpecs);
    }

    /**
     * Loads all dictionary files from the specified base directory and applies
     * custom dictionary files to selected slots.
     *
     * <p>The base official dictionaries are loaded first from {@code basePath};
     * then each {@link CustomDictSpec} patches its selected {@link DictSlot}.
     * Custom dictionary files are parsed with the same parser used for built-in
     * OpenCC text dictionaries.</p>
     *
     * <p>This method creates and returns a caller-owned dictionary. It does not
     * read from or modify {@link OpenCC.DictionaryHolder}. If {@code customSpecs}
     * is {@code null} or empty, this behaves like {@link #fromDicts(String)}.</p>
     *
     * @param basePath    the path to the directory containing official dictionary
     *                    {@code .txt} files; must not be {@code null}
     * @param customSpecs custom dictionary specs to apply; may be {@code null}
     *                    or empty
     * @return a newly loaded {@code DictionaryMaxlength} with custom patches applied
     * @throws RuntimeException     if an official or custom dictionary file cannot be loaded
     * @throws NullPointerException if {@code basePath} is {@code null} or
     *                              {@code customSpecs} contains a {@code null} spec
     */
    public static DictionaryMaxlength fromDicts(String basePath, List<CustomDictSpec> customSpecs) {
        final DictionaryMaxlength r = fromDicts(basePath);
        applyCustomDictSpecs(r, customSpecs);
        return r;
    }

    private static void applyCustomDictSpecs(
            DictionaryMaxlength dict,
            List<CustomDictSpec> customSpecs
    ) {
        if (customSpecs == null || customSpecs.isEmpty()) {
            return;
        }

        for (CustomDictSpec spec : customSpecs) {
            applyCustomDictSpec(dict, spec);
        }
    }

    /**
     * Applies a single custom dictionary specification to the target dictionary slot.
     *
     * <p>
     * This method is the centralized customization pipeline used by
     * {@code fromDicts(...)} and post-load customization APIs such as
     * {@code withCustomDicts(...)}.
     * </p>
     *
     * <p>
     * The target dictionary slot is resolved from {@link DictSlot} into the
     * internal {@link DictionaryMaxlength} field mapping. Depending on the
     * selected {@link CustomDictMode}, the existing slot content is either:
     * </p>
     *
     * <ul>
     *     <li>
     *         <b>Append</b> — merges custom entries on top of the existing
     *         OpenCC dictionary content.
     *     </li>
     *     <li>
     *         <b>Override</b> — fully replaces the target slot with only the
     *         supplied custom dictionary content.
     *     </li>
     * </ul>
     *
     * <p>
     * Custom dictionary files are applied first, followed by in-memory
     * dictionary pairs. If duplicate source keys exist, later entries replace
     * earlier ones using normal {@link Map#putAll(Map)} semantics.
     * </p>
     *
     * <p>
     * In-memory {@code pairs} are intentionally applied after external files,
     * allowing dynamically supplied application dictionaries to override file
     * based dictionaries when conflicts occur.
     * </p>
     *
     * <p>
     * After merging, the dictionary slot is rebuilt to recompute starter
     * indexes, maximum phrase lengths, and internal lookup structures required
     * by the conversion engine.
     * </p>
     *
     * @param dict Target dictionary instance to modify.
     * @param spec Custom dictionary specification describing the target slot,
     *             dictionary sources, and merge mode.
     * @throws NullPointerException     If {@code dict} or {@code spec} is {@code null}.
     * @throws IllegalArgumentException If the provided {@link DictSlot} is unsupported.
     * @throws RuntimeException         If a custom dictionary file cannot be loaded.
     */
    private static void applyCustomDictSpec(
            DictionaryMaxlength dict,
            CustomDictSpec spec
    ) {
        Objects.requireNonNull(dict, "dict");
        Objects.requireNonNull(spec, "spec");

        final String key = SLOT_KEYS.get(spec.slot);

        if (key == null) {
            throw new IllegalArgumentException(
                    "Unsupported DictSlot: " + spec.slot
            );
        }

        DictEntry existing = getDictEntry(dict, key);

        if (existing == null) {
            existing = new DictEntry(
                    new HashMap<>(),
                    1,
                    1
            );
        }

        final Map<String, String> merged;

        if (spec.mode == CustomDictMode.Override) {
            merged = new HashMap<>();
        } else {
            merged = new HashMap<>(existing.dict);
        }

        for (Path path : spec.paths) {
            try {
                DictEntry loaded = loadDictionaryMaxlength(path);
                merged.putAll(loaded.dict);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to load custom dictionary: " + path,
                        e
                );
            }
        }

        // Apply in-memory custom pairs after files.
        // If the same source key appears in both paths and pairs,
        // pairs win because they are applied last.
        merged.putAll(spec.pairs);

        DictEntry rebuilt = rebuildDictEntry(merged);

        setDictEntry(dict, key, rebuilt);
    }

    // Custom Dictionary Helpers
    private static DictEntry getDictEntry(DictionaryMaxlength dict, String key) {
        if ("st_characters".equals(key)) return dict.st_characters;
        if ("st_phrases".equals(key)) return dict.st_phrases;
        if ("st_punctuations".equals(key)) return dict.st_punctuations;

        if ("ts_characters".equals(key)) return dict.ts_characters;
        if ("ts_phrases".equals(key)) return dict.ts_phrases;
        if ("ts_punctuations".equals(key)) return dict.ts_punctuations;

        if ("tw_phrases".equals(key)) return dict.tw_phrases;
        if ("tw_phrases_rev".equals(key)) return dict.tw_phrases_rev;
        if ("tw_variants".equals(key)) return dict.tw_variants;
        if ("tw_variants_phrases".equals(key)) return dict.tw_variants_phrases;
        if ("tw_variants_rev".equals(key)) return dict.tw_variants_rev;
        if ("tw_variants_rev_phrases".equals(key)) return dict.tw_variants_rev_phrases;

        if ("hk_phrases".equals(key)) return dict.hk_phrases;
        if ("hk_phrases_rev".equals(key)) return dict.hk_phrases_rev;
        if ("hk_variants".equals(key)) return dict.hk_variants;
        if ("hk_variants_phrases".equals(key)) return dict.hk_variants_phrases;
        if ("hk_variants_rev".equals(key)) return dict.hk_variants_rev;
        if ("hk_variants_rev_phrases".equals(key)) return dict.hk_variants_rev_phrases;

        if ("jps_characters".equals(key)) return dict.jps_characters;
        if ("jps_characters_rev".equals(key)) return dict.jps_characters_rev;
        if ("jps_phrases".equals(key)) return dict.jps_phrases;

        throw new IllegalArgumentException("Unknown dictionary key: " + key);
    }

    private static void setDictEntry(DictionaryMaxlength dict, String key, DictEntry entry) {
        final BiConsumer<DictionaryMaxlength, DictEntry> setter = ASSIGN.get(key);

        if (setter == null) {
            throw new IllegalArgumentException("Unknown dictionary key: " + key);
        }

        setter.accept(dict, entry);
    }

    /**
     * Rebuilds a {@link DictEntry} from a merged dictionary map.
     *
     * <p>
     * This helper recalculates the minimum and maximum source phrase lengths
     * required by the OpenCC conversion engine after custom dictionary
     * modifications have been applied.
     * </p>
     *
     * <p>
     * The resulting {@link DictEntry} preserves the supplied dictionary map
     * instance and derives:
     * </p>
     *
     * <ul>
     *     <li>
     *         <b>maxLength</b> — the longest source phrase length.
     *     </li>
     *     <li>
     *         <b>minLength</b> — the shortest source phrase length.
     *     </li>
     * </ul>
     *
     * <p>
     * These values are later used by segmentation and phrase matching logic
     * to accelerate conversion lookups.
     * </p>
     *
     * <p>
     * If the supplied map is {@code null} or empty, a minimal empty
     * dictionary entry is returned with both lengths initialized to {@code 1}
     * to preserve internal conversion invariants.
     * </p>
     *
     * @param map Dictionary key-value mapping to rebuild.
     * @return A rebuilt {@link DictEntry} containing the supplied map and
     * recalculated phrase length metadata.
     */
    private static DictEntry rebuildDictEntry(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return new DictEntry(new HashMap<>(), 1, 1);
        }

        int maxLength = 1;
        int minLength = Integer.MAX_VALUE;

        for (String key : map.keySet()) {
            int len = key.length();

            if (len > maxLength) {
                maxLength = len;
            }

            if (len < minLength) {
                minLength = len;
            }
        }

        if (minLength == Integer.MAX_VALUE) {
            minLength = 1;
        }

        return new DictEntry(map, maxLength, minLength);
    }

    /**
     * Returns a customized copy of this dictionary with in-memory custom dictionary
     * pairs applied to selected slots.
     *
     * <p>This is the post-load customization path. The current instance is not
     * mutated. Instead, all dictionary entries are copied first and the custom
     * pairs are applied to the copy.</p>
     *
     * <p>If {@code specs} is {@code null} or empty, this method still returns a
     * separate copy with the same dictionary contents. This method does not read
     * from or modify {@link OpenCC.DictionaryHolder}.</p>
     *
     * @param specs custom dictionary specs to apply; may be {@code null} or empty
     * @return a customized copy of this dictionary
     * @throws NullPointerException if {@code specs} contains a {@code null} spec
     */
    public DictionaryMaxlength withCustomDicts(List<CustomDictSpec> specs) {
        DictionaryMaxlength copy = this.copy();
        applyCustomDictSpecs(copy, specs);
        return copy;
    }

    /**
     * Returns a customized copy of this dictionary with custom dictionary files
     * applied to selected slots.
     *
     * <p>This is the post-load customization path. The current instance is not
     * mutated. Instead, all dictionary entries are copied first and the custom
     * specs are applied to the copy. Custom dictionary files are parsed with the
     * same parser used for built-in OpenCC text dictionaries.</p>
     *
     * <p>If {@code specs} is {@code null} or empty, this method still returns a
     * separate copy with the same dictionary contents. This method does not read
     * from or modify {@link OpenCC.DictionaryHolder}.</p>
     *
     * @param specs custom dictionary specs to apply; may be {@code null} or empty
     * @return a customized copy of this dictionary
     * @throws RuntimeException     if a custom dictionary file cannot be loaded
     * @throws NullPointerException if {@code specs} contains a {@code null} spec
     */
    public DictionaryMaxlength withCustomDictFiles(List<CustomDictSpec> specs) {
        DictionaryMaxlength copy = this.copy();
        applyCustomDictSpecs(copy, specs);
        return copy;
    }

    private DictionaryMaxlength copy() {
        DictionaryMaxlength r = new DictionaryMaxlength();

        r.st_characters = copyEntry(this.st_characters);
        r.st_phrases = copyEntry(this.st_phrases);
        r.st_punctuations = copyEntry(this.st_punctuations);

        r.ts_characters = copyEntry(this.ts_characters);
        r.ts_phrases = copyEntry(this.ts_phrases);
        r.ts_punctuations = copyEntry(this.ts_punctuations);

        r.tw_phrases = copyEntry(this.tw_phrases);
        r.tw_phrases_rev = copyEntry(this.tw_phrases_rev);
        r.tw_variants = copyEntry(this.tw_variants);
        r.tw_variants_phrases = copyEntry(this.tw_variants_phrases);
        r.tw_variants_rev = copyEntry(this.tw_variants_rev);
        r.tw_variants_rev_phrases = copyEntry(this.tw_variants_rev_phrases);

        r.hk_phrases = copyEntry(this.hk_phrases);
        r.hk_phrases_rev = copyEntry(this.hk_phrases_rev);
        r.hk_variants = copyEntry(this.hk_variants);
        r.hk_variants_phrases = copyEntry(this.hk_variants_phrases);
        r.hk_variants_rev = copyEntry(this.hk_variants_rev);
        r.hk_variants_rev_phrases = copyEntry(this.hk_variants_rev_phrases);

        r.jps_characters = copyEntry(this.jps_characters);
        r.jps_characters_rev = copyEntry(this.jps_characters_rev);
        r.jps_phrases = copyEntry(this.jps_phrases);

        return r;
    }

    private static DictEntry copyEntry(DictEntry entry) {
        if (entry == null) {
            return null;
        }

        return new DictEntry(
                new HashMap<>(entry.dict),
                entry.maxLength,
                entry.minLength
        );
    }

    /**
     * Returns the mapping of dictionary identifiers to field setters.
     * <p>
     * Each entry links a dictionary name (e.g. {@code "st_characters"})
     * to a {@link BiConsumer} that assigns a loaded {@link DictEntry}
     * into the correct {@link DictionaryMaxlength} field.
     * </p>
     * <p>
     * The map preserves insertion order and is unmodifiable.
     * </p>
     *
     * @return an unmodifiable assignment map
     */
    private static Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> getAssign() {
        return ASSIGN;
    }

    // --- No-reflection field assignment table (Java 8 compatible) ---
    private static final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> ASSIGN;
    private static final Map<String, String> FILES;
    private static final Map<DictSlot, String> SLOT_KEYS;

    static {
        Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> m = new LinkedHashMap<>();

        m.put("st_characters", (o, e) -> o.st_characters = e);
        m.put("st_phrases", (o, e) -> o.st_phrases = e);
        m.put("st_punctuations", (o, e) -> o.st_punctuations = e);
        m.put("ts_characters", (o, e) -> o.ts_characters = e);
        m.put("ts_phrases", (o, e) -> o.ts_phrases = e);
        m.put("ts_punctuations", (o, e) -> o.ts_punctuations = e);
        m.put("tw_phrases", (o, e) -> o.tw_phrases = e);
        m.put("tw_phrases_rev", (o, e) -> o.tw_phrases_rev = e);
        m.put("tw_variants", (o, e) -> o.tw_variants = e);
        m.put("tw_variants_phrases", (o, e) -> o.tw_variants_phrases = e);
        m.put("tw_variants_rev", (o, e) -> o.tw_variants_rev = e);
        m.put("tw_variants_rev_phrases", (o, e) -> o.tw_variants_rev_phrases = e);
        m.put("hk_phrases", (o, e) -> o.hk_phrases = e);
        m.put("hk_phrases_rev", (o, e) -> o.hk_phrases_rev = e);
        m.put("hk_variants", (o, e) -> o.hk_variants = e);
        m.put("hk_variants_phrases", (o, e) -> o.hk_variants_phrases = e);
        m.put("hk_variants_rev", (o, e) -> o.hk_variants_rev = e);
        m.put("hk_variants_rev_phrases", (o, e) -> o.hk_variants_rev_phrases = e);
        m.put("jps_characters", (o, e) -> o.jps_characters = e);
        m.put("jps_characters_rev", (o, e) -> o.jps_characters_rev = e);
        m.put("jps_phrases", (o, e) -> o.jps_phrases = e);
        ASSIGN = Collections.unmodifiableMap(m);

        Map<String, String> f = new LinkedHashMap<>();
        f.put("st_characters", "STCharacters.txt");
        f.put("st_phrases", "STPhrases.txt");
        f.put("st_punctuations", "STPunctuations.txt");
        f.put("ts_characters", "TSCharacters.txt");
        f.put("ts_phrases", "TSPhrases.txt");
        f.put("ts_punctuations", "TSPunctuations.txt");
        f.put("tw_phrases", "TWPhrases.txt");
        f.put("tw_phrases_rev", "TWPhrasesRev.txt");
        f.put("tw_variants", "TWVariants.txt");
        f.put("tw_variants_phrases", "TWVariantsPhrases.txt");
        f.put("tw_variants_rev", "TWVariantsRev.txt");
        f.put("tw_variants_rev_phrases", "TWVariantsRevPhrases.txt");
        f.put("hk_phrases", "HKPhrases.txt");
        f.put("hk_phrases_rev", "HKPhrasesRev.txt");
        f.put("hk_variants", "HKVariants.txt");
        f.put("hk_variants_phrases", "HKVariantsPhrases.txt");
        f.put("hk_variants_rev", "HKVariantsRev.txt");
        f.put("hk_variants_rev_phrases", "HKVariantsRevPhrases.txt");
        f.put("jps_characters", "JPShinjitaiCharacters.txt");
        f.put("jps_characters_rev", "JPShinjitaiCharactersRev.txt");
        f.put("jps_phrases", "JPShinjitaiPhrases.txt");
        FILES = Collections.unmodifiableMap(f);

        Map<DictSlot, String> s = new EnumMap<>(DictSlot.class);
        s.put(DictSlot.STCharacters, "st_characters");
        s.put(DictSlot.STPhrases, "st_phrases");
        s.put(DictSlot.STPunctuations, "st_punctuations");
        s.put(DictSlot.TSCharacters, "ts_characters");
        s.put(DictSlot.TSPhrases, "ts_phrases");
        s.put(DictSlot.TSPunctuations, "ts_punctuations");
        s.put(DictSlot.TWPhrases, "tw_phrases");
        s.put(DictSlot.TWPhrasesRev, "tw_phrases_rev");
        s.put(DictSlot.TWVariants, "tw_variants");
        s.put(DictSlot.TWVariantsPhrases, "tw_variants_phrases");
        s.put(DictSlot.TWVariantsRev, "tw_variants_rev");
        s.put(DictSlot.TWVariantsRevPhrases, "tw_variants_rev_phrases");
        s.put(DictSlot.HKPhrases, "hk_phrases");
        s.put(DictSlot.HKPhrasesRev, "hk_phrases_rev");
        s.put(DictSlot.HKVariants, "hk_variants");
        s.put(DictSlot.HKVariantsPhrases, "hk_variants_phrases");
        s.put(DictSlot.HKVariantsRev, "hk_variants_rev");
        s.put(DictSlot.HKVariantsRevPhrases, "hk_variants_rev_phrases");
        s.put(DictSlot.JPSCharacters, "jps_characters");
        s.put(DictSlot.JPSCharactersRev, "jps_characters_rev");
        s.put(DictSlot.JPSPhrases, "jps_phrases");
        SLOT_KEYS = Collections.unmodifiableMap(s);
    }

    private static boolean isOptionalTextDictionary(String dictName) {
        return "hk_phrases".equals(dictName) || "hk_phrases_rev".equals(dictName);
    }

    private static void ensureOptionalHkPhraseEntries(DictionaryMaxlength r) {
        if (r.hk_phrases == null) r.hk_phrases = new DictEntry();
        if (r.hk_phrases_rev == null) r.hk_phrases_rev = new DictEntry();
    }

    /**
     * Returns the filename mapping for dictionary resources.
     * <p>
     * Each entry in the map associates a string key (e.g., {@code "st_characters"})
     * with the corresponding dictionary file name (e.g., {@code "STCharacters.txt"}).
     * This mapping is used by {@link #fromDicts(String)} to locate the dictionary
     * files either on the filesystem or on the classpath.
     * </p>
     * <p>
     * The returned map preserves insertion order and is unmodifiable.
     * Attempting to modify it will result in an {@link UnsupportedOperationException}.
     * </p>
     *
     * @return an unmodifiable map of dictionary identifiers to file names
     */
    private static Map<String, String> getFiles() {
        return FILES;
    }

    /**
     * Parses the content of a dictionary text file into a {@code DictEntry}.
     *
     * <p>Expected format per line:</p>
     * <ul>
     *   <li>Source phrase, followed by a TAB character (<code>'\t'</code>)</li>
     *   <li>Translation text, which may contain additional tokens after a space or tab;
     *       only the first token is used as the translation value</li>
     *   <li>Lines starting with <code>#</code> or <code>//</code>, or blank lines, are ignored</li>
     * </ul>
     *
     * <p>The maximum key length is computed using UTF-16 {@link String#length()}, so surrogate pairs
     * count as 2 code units. Surrogate handling is deferred to the conversion logic.</p>
     *
     * @param br a {@link BufferedReader} providing UTF-8 text from the dictionary file
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if an I/O error occurs while reading
     */
    private static DictEntry loadDictionaryMaxlength(BufferedReader br) throws IOException {
        Map<String, String> dict = new HashMap<>();
        int maxLength = 1;
        int minLength = Integer.MAX_VALUE;
        int lineNo = 0;

        for (String raw; (raw = br.readLine()) != null; ) {
            lineNo++;

            int start = 0;
            int end = raw.length();
            while (start < end && Character.isWhitespace(raw.charAt(start))) start++;
            while (end > start && Character.isWhitespace(raw.charAt(end - 1))) end--;
            if (start >= end) continue;

            char first = raw.charAt(start);
            if (first == '#') continue;
            if (first == '/' && start + 1 < end && raw.charAt(start + 1) == '/') continue;

            int tab = -1;
            for (int i = start; i < end; i++) {
                if (raw.charAt(i) == '	') {
                    tab = i;
                    break;
                }
            }
            if (tab < 0) {
                System.err.println("Warning: malformed (no TAB) at line " + lineNo + ": " + raw);
                continue;
            }

            int keyStart = start;

            if (lineNo == 1 && keyStart < tab && raw.charAt(keyStart) == '\uFEFF') keyStart++;
            if (keyStart >= tab) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }
            String key = raw.substring(keyStart, tab);

            int valueStart = tab + 1;
            while (valueStart < end) {
                char c = raw.charAt(valueStart);
                if (c != ' ' && c != '	') break;
                valueStart++;
            }
            if (valueStart >= end) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }

            int valueEnd = valueStart;
            while (valueEnd < end) {
                char c = raw.charAt(valueEnd);
                if (c == ' ' || c == '	') break;
                valueEnd++;
            }
            if (valueEnd <= valueStart) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }
            String val = raw.substring(valueStart, valueEnd);

            dict.put(key, val);
            int len = key.length();
            if (len > maxLength) maxLength = len;
            if (len < minLength) minLength = len;
        }
        if (dict.isEmpty()) {
            return new DictEntry(dict, 1, 1);
        } else {
            if (minLength == Integer.MAX_VALUE) minLength = 1;
            return new DictEntry(dict, maxLength, minLength);
        }
    }

    /**
     * Loads a dictionary file from the filesystem into a {@code DictEntry}.
     *
     * <p>This method opens the file as UTF-8 and parses it line by line using
     * {@link #loadDictionaryMaxlength(BufferedReader)}.</p>
     *
     * @param file the path to the dictionary text file
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if the file cannot be opened or read
     */
    private static DictEntry loadDictionaryMaxlength(Path file) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return loadDictionaryMaxlength(br);
        }
    }

    /**
     * Loads a dictionary file from an input stream into a {@code DictEntry}.
     *
     * <p>This method wraps the stream in a UTF-8 {@link BufferedReader} and parses it line by line
     * using {@link #loadDictionaryMaxlength(BufferedReader)}.</p>
     *
     * @param in an {@link InputStream} containing the UTF-8 dictionary text
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if an I/O error occurs while reading
     */
    private static DictEntry loadDictionaryMaxlength(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return loadDictionaryMaxlength(br);
        }
    }

    // --- Zero-dependency JSON loading ---

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON file.
     *
     * <p>This compatibility overload delegates to {@link #fromJsonNoDeps(Path)}.</p>
     *
     * @param jsonFile the JSON file to read
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJson(File jsonFile) throws IOException {
        Objects.requireNonNull(jsonFile, "jsonFile");
        return fromJsonNoDeps(jsonFile.toPath());
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON file path.
     *
     * <p>This compatibility overload delegates to {@link #fromJsonFileNoDeps(String)}.</p>
     *
     * @param path the JSON file path
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJson(String path) throws IOException {
        return fromJsonFileNoDeps(path);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON input stream.
     *
     * <p>This compatibility overload delegates to {@link #fromJsonNoDeps(InputStream)}.</p>
     *
     * @param in an input stream containing the JSON (UTF-8 encoded)
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJson(InputStream in) throws IOException {
        return fromJsonNoDeps(in);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON file without using external libraries.
     * <p>
     * This method expects the JSON to follow the {@code dictionary_maxlength.json} schema:
     * each top-level field maps to an array
     * {@code [ { "phrase": "translation", ... }, maxLength, minLength ]}.
     *
     * <p>For backward compatibility, older two-element snapshots
     * ({@code [ map, maxLength ]}) are also accepted and interpreted with
     * {@code minLength = 1} for non-empty dictionaries and {@code 0} for empty ones.</p>
     *
     * @param path the path to the JSON file (UTF-8 encoded)
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException              if reading the file fails
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonNoDeps(Path path) throws IOException {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(path);
        return hydrate(all);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON input stream without using external libraries.
     * <p>
     * This method expects the JSON to follow the {@code dictionary_maxlength.json} schema:
     * each top-level field maps to an array
     * {@code [ { "phrase": "translation", ... }, maxLength, minLength ]}.
     *
     * <p>For backward compatibility, older two-element snapshots
     * ({@code [ map, maxLength ]}) are also accepted and interpreted with
     * {@code minLength = 1} for non-empty dictionaries and {@code 0} for empty ones.</p>
     *
     * @param in an input stream containing the JSON (UTF-8 encoded)
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException              if reading from the stream fails
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonNoDeps(InputStream in) throws IOException {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(in);
        return hydrate(all);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON text string (no external deps).
     *
     * @param jsonString complete JSON document text
     * @return populated {@code DictionaryMaxlength}
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonStringNoDeps(String jsonString) {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(jsonString);
        return hydrate(all);
    }

    /**
     * Convenience: loads from a filesystem path string (delegates to the {@link java.nio.file.Path} overload).
     *
     * @param path JSON file path (UTF-8)
     * @return populated {@code DictionaryMaxlength}
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJsonFileNoDeps(String path) throws IOException {
        return fromJsonNoDeps(java.nio.file.Paths.get(path));
    }

    /**
     * Populates a new {@link DictionaryMaxlength} instance from a parsed
     * {@code Map<String, DictEntry>} as returned by {@link MiniDictJson}.
     *
     * <p>This method iterates over all top-level entries in the parsed map and,
     * if the key matches a known dictionary field (as defined in {@code ASSIGN}),
     * invokes the corresponding setter to store the {@link DictEntry} into
     * the new instance.</p>
     *
     * <p>Unknown keys are ignored, but a warning is printed to
     * {@code System.err} for diagnostic purposes.</p>
     *
     * @param all a map of field names to {@link DictEntry} objects,
     *            typically produced by {@link MiniDictJson#parseToMap}
     * @return a fully populated {@link DictionaryMaxlength} instance
     */
    private static DictionaryMaxlength hydrate(Map<String, DictEntry> all) {
        DictionaryMaxlength r = new DictionaryMaxlength();
        for (Map.Entry<String, DictEntry> kv : all.entrySet()) {
            BiConsumer<DictionaryMaxlength, DictEntry> setter = ASSIGN.get(kv.getKey());
            if (setter != null) {
                setter.accept(r, kv.getValue());
            } else {
                // Unknown key: ignore or log; your call
                System.err.println("Unknown dict key in JSON: " + kv.getKey());
            }
        }
        // Older JSON snapshots do not contain the direct HK phrase fields.
        // Initialize missing dictionaries as empty entries so all conversion plans are null-safe.
        ensureOptionalHkPhraseEntries(r);
        return r;
    }

    // --- Public no-deps serializer ---------------------------------------------

    /**
     * Serializes this {@code DictionaryMaxlength} to a JSON file without using
     * any external JSON dependency.
     *
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength, minLength ]}</p>
     *
     * <p>When {@code pretty} is {@code true}, the output includes indentation
     * and newlines. When {@code pretty} is {@code false}, the output is compact:
     * no indentation and no extra newlines. When {@code sortKeys} is
     * {@code true}, dictionary keys are written in deterministic lexical order
     * for stable diffs, debugging, and reproducible output. Sorting is for
     * reproducible output and readability, not deserialization speed.</p>
     *
     * <pre>{@code
     * DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();
     * dict.serializeToJson(Paths.get("dictionary_maxlength.json"), true, true);
     * }</pre>
     *
     * @param outputPath the target file path where UTF-8 JSON will be written
     * @param pretty     if {@code true}, write indentation and newlines; if
     *                   {@code false}, write compact JSON
     * @param sortKeys   if {@code true}, sort dictionary keys lexically for
     *                   deterministic output
     * @throws IOException if writing fails
     */
    public void serializeToJson(Path outputPath, boolean pretty, boolean sortKeys) throws IOException {
        try (Writer w = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8),
                1 << 20)) {
            writeJsonNoDeps(w, pretty, sortKeys);
            w.flush();
        }
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a JSON file without using
     * any external JSON dependency.
     *
     * <p>The file is written as UTF-8. See
     * {@link #serializeToJson(Path, boolean, boolean)} for details about
     * {@code pretty} and {@code sortKeys}.</p>
     *
     * <pre>{@code
     * DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();
     * dict.serializeToJson("dictionary_maxlength.json", false, true);
     * }</pre>
     *
     * @param outputPath the target file path where UTF-8 JSON will be written
     * @param pretty     if {@code true}, write indentation and newlines; if
     *                   {@code false}, write compact JSON
     * @param sortKeys   if {@code true}, sort dictionary keys lexically for
     *                   deterministic output
     * @throws IOException if writing fails
     */
    public void serializeToJson(String outputPath, boolean pretty, boolean sortKeys) throws IOException {
        serializeToJson(Paths.get(outputPath), pretty, sortKeys);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a JSON string without
     * using any external JSON dependency.
     *
     * <p>When {@code pretty} is {@code true}, the returned string includes
     * indentation and newlines. When {@code pretty} is {@code false}, the
     * returned string is compact: no indentation and no extra newlines. When
     * {@code sortKeys} is {@code true}, dictionary keys are written in
     * deterministic lexical order for stable diffs, debugging, and reproducible
     * output. Sorting is for reproducible output and readability, not
     * deserialization speed.</p>
     *
     * <pre>{@code
     * DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();
     * String json = dict.serializeToJsonString(false, false);
     * }</pre>
     *
     * @param pretty   if {@code true}, write indentation and newlines; if
     *                 {@code false}, write compact JSON
     * @param sortKeys if {@code true}, sort dictionary keys lexically for
     *                 deterministic output
     * @return JSON text held as a Java {@code String}
     * @throws IOException if an I/O error occurs while generating the JSON
     */
    public String serializeToJsonString(boolean pretty, boolean sortKeys) throws IOException {
        StringWriter sw = new StringWriter(1 << 20);
        writeJsonNoDeps(sw, pretty, sortKeys);
        return sw.toString();
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to pretty JSON without
     * external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength, minLength ]}</p>
     *
     * @param outputPath path to write as UTF-8
     * @throws IOException if writing fails
     */
    public void serializeToJsonNoDeps(String outputPath) throws IOException {
        serializeToJson(Paths.get(outputPath), true, true);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to pretty JSON without
     * external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength, minLength ]}</p>
     *
     * @param outputPath the target file path where UTF-8 JSON will be written
     * @throws IOException if writing fails
     */
    public void serializeToJsonNoDeps(Path outputPath) throws IOException {
        serializeToJson(outputPath, true, true);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a compact JSON string
     * without external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength, minLength ]}</p>
     *
     * @return compact JSON text held as a Java {@code String}
     * @throws IOException if an I/O error occurs while generating the JSON
     */
    public String serializeToJsonStringNoDeps() throws IOException {
        return serializeToJsonString(false, false);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a compact JSON string
     * without external libraries.
     *
     * @return compact JSON text held as a Java {@code String}
     * @throws IOException if serialization fails
     */
    public String serializeToJsonStringNoDepsCompact() throws IOException {
        return serializeToJsonString(false, false);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a compact JSON file
     * without external libraries.
     * <p>Compact form means no indentation and no extra newlines.</p>
     *
     * @param outputPath destination path where UTF-8 JSON will be written
     * @throws IOException if writing fails
     */
    public void serializeToJsonFileNoDepsCompact(Path outputPath) throws IOException {
        serializeToJson(outputPath, false, false);
    }

    // --- Implementation ----------------------------------------------------------

    /**
     * Convenience overload of {@link #writeJsonNoDeps(Writer, boolean, boolean)}
     * that defaults to sorting keys in deterministic (UTF-16 natural) order.
     *
     * @param w      the {@link Writer} to receive the JSON output (UTF-8 recommended)
     * @param pretty if {@code true}, output will be pretty-printed with newlines
     *               and indentation; if {@code false}, output will be compact
     * @throws IOException if writing to {@code w} fails
     */
    private void writeJsonNoDeps(Writer w, boolean pretty) throws IOException {
        writeJsonNoDeps(w, pretty, /*sortKeys*/ true); // or false for original order
    }

    /**
     * Writes this {@link DictionaryMaxlength} instance as JSON without using
     * any external libraries.
     *
     * <p>The output format is schema-specific to OpenCC dictionaries:
     * each field is written as {@code "name": [ { "key": "value", ... }, maxLength, minLength ]}
     * with optional pretty-printing and deterministic key ordering.</p>
     *
     * @param w        the {@link Writer} to receive the JSON output (UTF-8 recommended)
     * @param pretty   if {@code true}, output will be pretty-printed with newlines
     *                 and indentation; if {@code false}, output will be compact
     * @param sortKeys if {@code true}, dictionary keys will be written in sorted
     *                 (UTF-16 natural) order for deterministic output; if {@code false},
     *                 keys will be written in their map iteration order
     * @throws IOException if writing to {@code w} fails
     */
    private void writeJsonNoDeps(Writer w, boolean pretty, boolean sortKeys) throws IOException {
        final String nl = pretty ? "\n" : "";
        final String ind1 = pretty ? "  " : "";
        final String ind2 = pretty ? "    " : "";
        boolean firstField;

        w.write("{");
        w.write(nl);
        firstField = writeField(w, "st_characters", st_characters, true, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "st_phrases", st_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "st_punctuations", st_punctuations, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "ts_characters", ts_characters, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "ts_phrases", ts_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "ts_punctuations", ts_punctuations, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_phrases", tw_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_phrases_rev", tw_phrases_rev, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_variants", tw_variants, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_variants_phrases", tw_variants_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_variants_rev", tw_variants_rev, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "tw_variants_rev_phrases", tw_variants_rev_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_phrases", hk_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_phrases_rev", hk_phrases_rev, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_variants", hk_variants, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_variants_phrases", hk_variants_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_variants_rev", hk_variants_rev, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "hk_variants_rev_phrases", hk_variants_rev_phrases, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "jps_characters", jps_characters, firstField, ind1, ind2, nl, sortKeys);
        firstField = writeField(w, "jps_characters_rev", jps_characters_rev, firstField, ind1, ind2, nl, sortKeys);
        writeField(w, "jps_phrases", jps_phrases, firstField, ind1, ind2, nl, sortKeys);
        w.write(nl);
        w.write("}");
        w.write(nl);
    }

    /**
     * Writes a single {@link DictEntry} field to the JSON output.
     *
     * <p>Output format per field:</p>
     * <pre>{@code
     *   "name": [ { "k": "v", ... }, maxLength, minLength ]
     * }</pre>
     *
     * @param w          the {@link Writer} to write JSON to
     * @param name       the JSON field name (dictionary block name)
     * @param entry      the {@link DictEntry} containing the mapping and max length
     * @param firstField whether this is the first field in the enclosing object;
     *                   if {@code false}, a leading comma will be written before this field
     * @param ind1       indentation for the first level (used only if pretty-printing)
     * @param ind2       indentation for the second level (used only if pretty-printing)
     * @param nl         newline string (empty if not pretty-printing)
     * @param sortKeys   if {@code true}, dictionary keys will be written in sorted (UTF-16) order
     *                   for deterministic output; if {@code false}, keys will be written in
     *                   the map's natural iteration order
     * @return {@code false} to indicate that subsequent calls are not the first field anymore
     * @throws IOException if writing to the output fails
     *
     *                     <p><strong>Note:</strong> Sorting is used solely for deterministic serialization
     *                     (e.g., reproducible builds or test comparisons). It is not required for
     *                     functional correctness and may be disabled to preserve insertion order.</p>
     */
    private boolean writeField(
            Writer w, String name, DictEntry entry, boolean firstField,
            String ind1, String ind2, String nl, boolean sortKeys) throws IOException {

        if (entry == null) return firstField;

        if (!firstField) {
            w.write(",");
            w.write(nl);
        }
        w.write(ind1);
        w.write("\"");
        writeJsonString(name, w);
        w.write("\": [ ");
        w.write(nl);

        // object start
        w.write(ind2);
        w.write("{");
        w.write(nl);

        boolean firstKV = true;

        if (sortKeys) {
            // Deterministic lexical order for stable generated JSON.
            String[] keys = entry.dict.keySet().toArray(new String[0]);
            java.util.Arrays.sort(keys);

            for (String k : keys) {
                if (!firstKV) {
                    w.write(",");
                    w.write(nl);
                }
                w.write(ind2);
                w.write(ind1);
                w.write("\"");
                writeJsonString(k, w);
                w.write("\": ");
                w.write("\"");
                writeJsonString(entry.dict.get(k), w);
                w.write("\"");
                firstKV = false;
            }
        } else {
            for (java.util.Map.Entry<String, String> kv : entry.dict.entrySet()) {
                if (!firstKV) {
                    w.write(",");
                    w.write(nl);
                }
                w.write(ind2);
                w.write(ind1);
                w.write("\"");
                writeJsonString(kv.getKey(), w);
                w.write("\": ");
                w.write("\"");
                writeJsonString(kv.getValue(), w);
                w.write("\"");
                firstKV = false;
            }
        }

        w.write(nl);
        w.write(ind2);
        w.write("}");
        w.write(", ");
        w.write(String.valueOf(entry.maxLength));
        // NEW: always write minLength in new snapshots
        w.write(", ");
        w.write(Integer.toString(entry.minLength));
        w.write(" ]");
        return false;
    }

    /**
     * Minimal JSON string escaper. Writes directly to {@code w}.
     */
    private static void writeJsonString(String s, Writer w) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"':
                    w.write("\\\"");
                    break;
                case '\\':
                    w.write("\\\\");
                    break;
                case '\b':
                    w.write("\\b");
                    break;
                case '\f':
                    w.write("\\f");
                    break;
                case '\n':
                    w.write("\\n");
                    break;
                case '\r':
                    w.write("\\r");
                    break;
                case '\t':
                    w.write("\\t");
                    break;
                default:
                    if (c < 0x20) { // control char → \\u00XX
                        w.write("\\u00");
                        String hex = Integer.toHexString(c);
                        if (hex.length() == 1) w.write('0');
                        w.write(hex);
                    } else {
                        // write raw Unicode (Jackson may escape non-ASCII, but JSON allows raw UTF-8)
                        w.write(c);
                    }
            }
        }
    }
}
