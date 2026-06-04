package openccjava;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Represents a container for all OpenCC dictionary mappings,
 * including phrase-level and character-level dictionaries.
 *
 * <p>This class supports loading from:
 * <ul>
 *     <li>JSON-serialized form (used in deployments)</li>
 *     <li>Individual dictionary text files (used during development or as fallback)</li>
 * </ul>
 *
 * <p>Each dictionary is stored as a {@link DictEntry} with:
 * <ul>
 *     <li>{@code dict}: key-value pairs of source→target</li>
 *     <li>{@code maxLength}: the longest phrase/key length</li>
 *     <li>{@code minLength}: the shortest phrase/key length</li>
 * </ul>
 * Holds multiple dictionary entries, each with defined key-length metadata.
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
     * Represents a dictionary entry with mapping data and key-length metadata.
     */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
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
        public int minLength;

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
         * Creates a dictionary entry from the public array-shaped JSON contract.
         *
         * <p>The current format is {@code [dict, maxLength, minLength]}. Older
         * snapshots that only stored {@code [dict, maxLength]} are also accepted,
         * and derive {@code minLength} from the dictionary contents.</p>
         *
         * @param dict      dictionary mapping strings
         * @param maxLength maximum key length
         * @param minLength minimum key length; may be {@code null} for older snapshots
         */
        @JsonCreator
        public DictEntry(
                @JsonProperty(index = 0, value = "dict") Map<String, String> dict,
                @JsonProperty(index = 1, value = "maxLength") Integer maxLength,
                @JsonProperty(index = 2, value = "minLength") Integer minLength
        ) {
            this.dict = dict == null ? new HashMap<String, String>() : new HashMap<>(dict);
            this.maxLength = maxLength == null ? 0 : maxLength;
            this.minLength = minLength == null ? computeMinLength(this.dict) : minLength;
        }

        /**
         * Constructs a new dictionary entry.
         *
         * @param dict      The dictionary mapping strings.
         * @param maxLength The maximum key length in the dictionary.
         * @param minLength The minimum key length in the dictionary.
         */
        public DictEntry(Map<String, String> dict, int maxLength, int minLength) {
            this.dict = new HashMap<>(dict);
            this.maxLength = maxLength;
            this.minLength = minLength;
        }

        private static int computeMinLength(Map<String, String> dict) {
            if (dict == null || dict.isEmpty()) {
                return 0;
            }
            int min = Integer.MAX_VALUE;
            for (String key : dict.keySet()) {
                if (key != null) {
                    min = Math.min(min, key.length());
                }
            }
            return min == Integer.MAX_VALUE ? 0 : min;
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
     * Japanese Shinjitai character mappings.
     */
    public DictEntry jps_characters;
    /**
     * Japanese Shinjitai phrase mappings.
     */
    public DictEntry jps_phrases;
    /**
     * Japanese variant mappings.
     */
    public DictEntry jp_variants;
    /**
     * Japanese variant reverse mappings.
     */
    public DictEntry jp_variants_rev;

    /**
     * Returns a human-readable summary showing how many dictionaries are loaded.
     */
    @Override
    public String toString() {
        long count = Arrays.stream(this.getClass().getFields())
                .filter(f -> {
                    try {
                        DictEntry entry = (DictEntry) f.get(this);
                        return entry != null && entry.dict != null && !entry.dict.isEmpty();
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                })
                .count();
        return "<DictionaryMaxlength with " + count + " loaded dicts>";
    }

    /**
     * Loads a {@code DictionaryMaxlength} instance from a JSON file.
     *
     * @param jsonFile the JSON file to read
     * @return the parsed {@code DictionaryMaxlength} instance
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJson(File jsonFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonFile, DictionaryMaxlength.class);
    }

    /**
     * Loads a {@code DictionaryMaxlength} instance from a JSON file path.
     *
     * @param path the file path
     * @return the parsed {@code DictionaryMaxlength} instance
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJson(String path) throws IOException {
        return fromJson(new File(path));
    }

    /**
     * Loads a {@code DictionaryMaxlength} instance from a JSON input stream.
     * <p>
     * This method is typically used to load a precompiled dictionary from a resource
     * bundled within the application JAR or classpath (e.g., {@code /dicts/dictionary_maxlength.json}).
     * It is useful when the JSON file is embedded as a resource rather than stored on the filesystem.
     * </p>
     *
     * @param in the input stream containing the JSON data
     * @return the populated {@code DictionaryMaxlength} object
     * @throws IOException if the JSON cannot be read or parsed
     */
    public static DictionaryMaxlength fromJson(InputStream in) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(in, DictionaryMaxlength.class);
    }

    /**
     * Loads all OpenCC dictionary files from the default {@code "dicts"} directory.
     * <p>
     * This is a convenience overload of {@link #fromDicts(String)}, equivalent to
     * calling {@code fromDicts("dicts")}.
     * </p>
     *
     * @return a fully populated {@link DictionaryMaxlength} instance
     * with all dictionary fields assigned
     * @throws RuntimeException if any dictionary file cannot be loaded
     */
    public static DictionaryMaxlength fromDicts() {
        return fromDicts("dicts");
    }

    /**
     * Loads all OpenCC dictionary files from the default {@code "dicts"} directory,
     * then applies custom dictionary specs to selected slots.
     *
     * @param customSpecs custom dictionary patches; {@code null} is treated as empty
     * @return a fully populated and patched {@link DictionaryMaxlength} instance
     */
    public static DictionaryMaxlength fromDicts(List<CustomDictSpec> customSpecs) {
        return fromDicts("dicts", customSpecs);
    }

    /**
     * Loads all OpenCC dictionary files from the specified base directory.
     * <p>
     * For each required dictionary:
     * </p>
     * <ol>
     *   <li>First attempts to load from the filesystem path
     *       {@code basePath/filename}.</li>
     *   <li>If the file does not exist, falls back to loading a classpath
     *       resource {@code /basePath/filename}.</li>
     *   <li>Each loaded dictionary is wrapped in a {@link DictEntry} and
     *       assigned into the corresponding field of the returned
     *       {@link DictionaryMaxlength} via the {@link #ASSIGN} table.</li>
     * </ol>
     *
     * <p>
     * The loading order is fixed according to {@link #getFiles()}, ensuring
     * consistent assignment of phrase, character, and variant dictionaries
     * across Simplified, Traditional, Taiwan, Hong Kong, and Japan sets.
     * </p>
     *
     * @param basePath the directory (filesystem or classpath) containing
     *                 dictionary {@code .txt} files
     * @return a fully populated {@link DictionaryMaxlength} instance
     * @throws RuntimeException if a dictionary file is missing or fails to load
     */
    public static DictionaryMaxlength fromDicts(String basePath) {
        final DictionaryMaxlength r = new DictionaryMaxlength();

        // ----- filenames (ordered, then wrapped unmodifiable) -----
        final Map<String, String> files = getFiles();

        // ----- assignment table (ordered, then wrapped unmodifiable) -----
        final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> assign = getAssign();

        files.keySet().forEach(name -> {
            if (!assign.containsKey(name)) {
                throw new IllegalStateException(
                        "Missing assignment mapping for dictionary '" + name +
                                "' (filename=" + files.get(name) + ")");
            }
        });

        for (Map.Entry<String, String> kv : files.entrySet()) {
            final String dictName = kv.getKey();
            final String filename = kv.getValue();

            final Path fsPath = Paths.get(basePath, filename);
            try {
                final DictEntry entry;
                if (Files.exists(fsPath)) {
                    entry = loadDictionaryMaxlength(fsPath);
                } else {
                    final String resPath = "/" + basePath + "/" + filename;
                    try (InputStream in = DictionaryMaxlength.class.getResourceAsStream(resPath)) {
                        if (in == null) throw new FileNotFoundException("Missing resource: " + resPath +
                                " (also checked FS: " + fsPath.toAbsolutePath() + ")");
                        entry = loadDictionaryMaxlength(in);
                    }
                }

                final BiConsumer<DictionaryMaxlength, DictEntry> setter = assign.get(dictName);
                if (setter == null) {
                    throw new IllegalStateException("No assign mapping for dict: " + dictName);
                }
                setter.accept(r, entry);

            } catch (IOException ex) {
                throw new RuntimeException("Error loading dict: " + dictName + " (" + filename + ")", ex);
            }
        }

        return r;
    }

    /**
     * Loads all OpenCC dictionary files from the specified base directory, then
     * applies custom dictionary specs to selected slots.
     *
     * @param basePath    dictionary directory
     * @param customSpecs custom dictionary patches; {@code null} is treated as empty
     * @return a fully populated and patched {@link DictionaryMaxlength} instance
     */
    public static DictionaryMaxlength fromDicts(String basePath, List<CustomDictSpec> customSpecs) {
        DictionaryMaxlength r = fromDicts(basePath);
        applyCustomDictSpecs(r, customSpecs);
        return r;
    }

    private static void applyCustomDictSpecs(DictionaryMaxlength dict, List<CustomDictSpec> specs) {
        if (specs == null) return;
        for (CustomDictSpec spec : specs) {
            applyCustomDictSpec(dict, spec);
        }
    }

    private static void applyCustomDictSpec(DictionaryMaxlength dict, CustomDictSpec spec) {
        Objects.requireNonNull(dict, "dict");
        Objects.requireNonNull(spec, "spec");

        String key = SLOT_KEYS.get(spec.slot);
        if (key == null) {
            throw new IllegalArgumentException("Unsupported DictSlot: " + spec.slot);
        }

        DictEntry current = getEntry(dict, key);
        Map<String, String> merged = new LinkedHashMap<>();
        if (spec.mode == CustomDictMode.Append && current != null && current.dict != null) {
            merged.putAll(current.dict);
        }

        try {
            for (Path path : spec.paths) {
                merged.putAll(loadDictionaryMaxlength(path).dict);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error loading custom dict for slot: " + spec.slot, ex);
        }
        merged.putAll(spec.pairs);

        getAssign().get(key).accept(dict, new DictEntry(merged, computeMaxLength(merged), computeMinLength(merged)));
    }

    /**
     * Returns a copied dictionary with custom in-memory dictionary specs applied.
     *
     * @param specs custom dictionary specs; {@code null} is treated as empty
     * @return copied and patched dictionary
     */
    public DictionaryMaxlength withCustomDicts(List<CustomDictSpec> specs) {
        DictionaryMaxlength copy = this.copy();
        applyCustomDictSpecs(copy, specs);
        return copy;
    }

    /**
     * Returns a copied dictionary with custom dictionary file specs applied.
     *
     * @param specs custom dictionary specs; {@code null} is treated as empty
     * @return copied and patched dictionary
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

        r.hk_variants = copyEntry(this.hk_variants);
        r.hk_variants_phrases = copyEntry(this.hk_variants_phrases);
        r.hk_variants_rev = copyEntry(this.hk_variants_rev);
        r.hk_variants_rev_phrases = copyEntry(this.hk_variants_rev_phrases);

        r.jps_characters = copyEntry(this.jps_characters);
        r.jps_phrases = copyEntry(this.jps_phrases);
        r.jp_variants = copyEntry(this.jp_variants);
        r.jp_variants_rev = copyEntry(this.jp_variants_rev);

        return r;
    }

    private static DictEntry copyEntry(DictEntry entry) {
        if (entry == null) return null;
        return new DictEntry(entry.dict, entry.maxLength, entry.minLength);
    }

    // --- No-reflection field assignment table (Java 8 compatible) ---

    /**
     * Assignment table mapping dictionary names (as keys in {@link #getFiles()})
     * to setters that populate the corresponding fields of
     * {@link DictionaryMaxlength}.
     * <p>
     * This avoids the use of reflection for field assignment, maintaining
     * Java&nbsp;8 compatibility and predictable performance.
     * </p>
     */
    private static final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> ASSIGN;
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
        m.put("hk_variants", (o, e) -> o.hk_variants = e);
        m.put("hk_variants_phrases", (o, e) -> o.hk_variants_phrases = e);
        m.put("hk_variants_rev", (o, e) -> o.hk_variants_rev = e);
        m.put("hk_variants_rev_phrases", (o, e) -> o.hk_variants_rev_phrases = e);
        m.put("jps_characters", (o, e) -> o.jps_characters = e);
        m.put("jps_phrases", (o, e) -> o.jps_phrases = e);
        m.put("jp_variants", (o, e) -> o.jp_variants = e);
        m.put("jp_variants_rev", (o, e) -> o.jp_variants_rev = e);

        ASSIGN = Collections.unmodifiableMap(m);

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
        s.put(DictSlot.HKVariants, "hk_variants");
        s.put(DictSlot.HKVariantsPhrases, "hk_variants_phrases");
        s.put(DictSlot.HKVariantsRev, "hk_variants_rev");
        s.put(DictSlot.HKVariantsRevPhrases, "hk_variants_rev_phrases");
        s.put(DictSlot.JPSCharacters, "jps_characters");
        s.put(DictSlot.JPSPhrases, "jps_phrases");
        s.put(DictSlot.JPVariants, "jp_variants");
        s.put(DictSlot.JPVariantsRev, "jp_variants_rev");
        SLOT_KEYS = Collections.unmodifiableMap(s);
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
        final Map<String, String> files;
        {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("st_characters", "STCharacters.txt");
            m.put("st_phrases", "STPhrases.txt");
            m.put("st_punctuations", "STPunctuations.txt");
            m.put("ts_characters", "TSCharacters.txt");
            m.put("ts_phrases", "TSPhrases.txt");
            m.put("ts_punctuations", "TSPunctuations.txt");
            m.put("tw_phrases", "TWPhrases.txt");
            m.put("tw_phrases_rev", "TWPhrasesRev.txt");
            m.put("tw_variants", "TWVariants.txt");
            m.put("tw_variants_phrases", "TWVariantsPhrases.txt");
            m.put("tw_variants_rev", "TWVariantsRev.txt");
            m.put("tw_variants_rev_phrases", "TWVariantsRevPhrases.txt");
            m.put("hk_variants", "HKVariants.txt");
            m.put("hk_variants_phrases", "HKVariantsPhrases.txt");
            m.put("hk_variants_rev", "HKVariantsRev.txt");
            m.put("hk_variants_rev_phrases", "HKVariantsRevPhrases.txt");
            m.put("jps_characters", "JPShinjitaiCharacters.txt");
            m.put("jps_phrases", "JPShinjitaiPhrases.txt");
            m.put("jp_variants", "JPVariants.txt");
            m.put("jp_variants_rev", "JPVariantsRev.txt");
            files = Collections.unmodifiableMap(m);
        }
        return files;
    }

    private static DictEntry getEntry(DictionaryMaxlength dict, String key) {
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
        if ("hk_variants".equals(key)) return dict.hk_variants;
        if ("hk_variants_phrases".equals(key)) return dict.hk_variants_phrases;
        if ("hk_variants_rev".equals(key)) return dict.hk_variants_rev;
        if ("hk_variants_rev_phrases".equals(key)) return dict.hk_variants_rev_phrases;
        if ("jps_characters".equals(key)) return dict.jps_characters;
        if ("jps_phrases".equals(key)) return dict.jps_phrases;
        if ("jp_variants".equals(key)) return dict.jp_variants;
        if ("jp_variants_rev".equals(key)) return dict.jp_variants_rev;
        throw new IllegalArgumentException("Unsupported dictionary key: " + key);
    }

    private static int computeMaxLength(Map<String, String> dict) {
        int max = 0;
        if (dict == null) return max;
        for (String key : dict.keySet()) {
            if (key != null && key.length() > max) {
                max = key.length();
            }
        }
        return max;
    }

    private static int computeMinLength(Map<String, String> dict) {
        if (dict == null || dict.isEmpty()) return 0;
        int min = Integer.MAX_VALUE;
        for (String key : dict.keySet()) {
            if (key != null && key.length() < min) {
                min = key.length();
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * Parses the contents of a dictionary text file into a {@link DictEntry}.
     *
     * <p>File format rules:</p>
     * <ul>
     *   <li>Each line contains a source phrase, a TAB, and a translation value.</li>
     *   <li>The translation is taken as the first token after the TAB, ending at the
     *       first space or TAB.</li>
     *   <li>Blank lines and lines starting with {@code #} or {@code //} are ignored.</li>
     *   <li>If the first key starts with a BOM ({@code U+FEFF}), it is stripped.</li>
     * </ul>
     *
     * <p>The maximum key length is measured using UTF-16 {@link String#length()}.
     * Surrogate pairs therefore count as two code units; surrogate handling is left
     * to the conversion logic.</p>
     *
     * @param br a reader supplying UTF-8 text
     * @return a {@link DictEntry} containing the parsed key–value pairs and maximum + minimum phrase length
     * @throws IOException if an I/O error occurs while reading
     */
    private static DictEntry loadDictionaryMaxlength(BufferedReader br) throws IOException {
        Map<String, String> dict = new HashMap<>();
        int maxLength = 0;
        int minLength = Integer.MAX_VALUE;
        int lineNo = 0;

        for (String raw; (raw = br.readLine()) != null; ) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            int tab = line.indexOf('\t');
            if (tab < 0) {
                System.err.println("Warning: malformed (no TAB) at line " + lineNo + ": " + raw);
                continue;
            }

            String key = line.substring(0, tab);
            if (lineNo == 1 && !key.isEmpty() && key.charAt(0) == '\uFEFF') {
                key = key.substring(1); // strip BOM
            }

            // first token after TAB (space OR tab ends it)
            String val = getRestString(line, tab);

            if (key.isEmpty() || val.isEmpty()) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }

            dict.put(key, val);

            int len = key.length(); // UTF-16 length (non-BMP counts as 2)
            if (len > maxLength) maxLength = len;
            if (len < minLength) minLength = len;
        }

        if (dict.isEmpty()) {
            // empty dict → both lengths 0
            maxLength = 0;
            minLength = 0;
        }

        return new DictEntry(dict, maxLength, minLength);
    }

    /**
     * Extracts the first token after a TAB delimiter in a dictionary line.
     *
     * <p>Leading spaces and TABs after the delimiter are skipped. The token ends
     * at the next space or TAB, or at the end of the line if none is found.</p>
     *
     * @param line the full dictionary line
     * @param tab  the index of the TAB delimiter
     * @return the trimmed first token after the TAB
     */
    private static String getRestString(String line, int tab) {
        String rest = line.substring(tab + 1);
        // Java 8 replacement for stripLeading()
        int idx = 0;
        while (idx < rest.length() && (rest.charAt(idx) == ' ' || rest.charAt(idx) == '\t')) {
            idx++;
        }
        rest = rest.substring(idx);

        int end = -1;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ' ' || c == '\t') {
                end = i;
                break;
            }
        }
        return (end >= 0) ? rest.substring(0, end) : rest;
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

    /**
     * Serializes this {@code DictionaryMaxlength} object to a JSON file.
     *
     * @param outputPath the output path where the JSON should be written
     * @throws RuntimeException if writing the file fails
     */
    public void serializeToJson(String outputPath) {
        try {
            serializeToJson(Paths.get(outputPath), true, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to: " + outputPath, e);
        }
    }

    /**
     * Serializes this dictionary to JSON with stable field order and optional
     * sorted dictionary keys.
     *
     * @param outputPath output file path
     * @param pretty     whether to write indented JSON
     * @param sortKeys   whether dictionary map keys should be sorted
     * @throws IOException if writing fails
     */
    public void serializeToJson(Path outputPath, boolean pretty, boolean sortKeys) throws IOException {
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
            ObjectMapper mapper = new ObjectMapper();
            Object value = toSerializableMap(sortKeys);
            if (pretty) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, value);
            } else {
                mapper.writeValue(writer, value);
            }
        }
    }

    /**
     * Serializes this dictionary to JSON with stable field order and optional
     * sorted dictionary keys.
     *
     * @param outputPath output file path
     * @param pretty     whether to write indented JSON
     * @param sortKeys   whether dictionary map keys should be sorted
     * @throws IOException if writing fails
     */
    public void serializeToJson(String outputPath, boolean pretty, boolean sortKeys) throws IOException {
        serializeToJson(Paths.get(outputPath), pretty, sortKeys);
    }

    /**
     * Serializes this dictionary to a JSON string using the same stable field
     * ordering as file serialization.
     *
     * @param pretty   whether to write indented JSON
     * @param sortKeys whether dictionary map keys should be sorted
     * @return serialized JSON
     * @throws IOException if serialization fails
     */
    public String serializeToJsonString(boolean pretty, boolean sortKeys) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Object value = toSerializableMap(sortKeys);
        if (pretty) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        }
        return mapper.writeValueAsString(value);
    }

    private Map<String, Object> toSerializableMap(boolean sortKeys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : getFiles().keySet()) {
            out.put(key, toSerializableEntry(getEntry(this, key), sortKeys));
        }
        return out;
    }

    private static Object[] toSerializableEntry(DictEntry entry, boolean sortKeys) {
        if (entry == null) return null;

        Map<String, String> dict = new LinkedHashMap<>();
        if (entry.dict != null) {
            Map<String, String> source = sortKeys
                    ? new TreeMap<>(entry.dict)
                    : entry.dict;
            dict.putAll(source);
        }

        return new Object[]{dict, entry.maxLength, entry.minLength};
    }
}


