package openccjava;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
 * </ul>
 * Holds multiple dictionary entries, each with a defined maximum key length.
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
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            entry = loadDictionaryMaxlength(br);
                        }
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
        m.put("tw_variants_rev", (o, e) -> o.tw_variants_rev = e);
        m.put("tw_variants_rev_phrases", (o, e) -> o.tw_variants_rev_phrases = e);
        m.put("hk_variants", (o, e) -> o.hk_variants = e);
        m.put("hk_variants_rev", (o, e) -> o.hk_variants_rev = e);
        m.put("hk_variants_rev_phrases", (o, e) -> o.hk_variants_rev_phrases = e);
        m.put("jps_characters", (o, e) -> o.jps_characters = e);
        m.put("jps_phrases", (o, e) -> o.jps_phrases = e);
        m.put("jp_variants", (o, e) -> o.jp_variants = e);
        m.put("jp_variants_rev", (o, e) -> o.jp_variants_rev = e);

        ASSIGN = Collections.unmodifiableMap(m);
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
            m.put("tw_variants_rev", "TWVariantsRev.txt");
            m.put("tw_variants_rev_phrases", "TWVariantsRevPhrases.txt");
            m.put("hk_variants", "HKVariants.txt");
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
        ObjectMapper mapper = new ObjectMapper();
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(Paths.get(outputPath)), StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to: " + outputPath, e);
        }
    }

    // NEW: union slots
    /**
     * Holds lazily computed {@link StarterUnion} instances for each {@link UnionKey}.
     * <p>
     * A {@code StarterUnion} represents the set of all possible starter characters
     * (first code points of dictionary keys) for a particular conversion union.
     * Unions are built only when first requested via {@link #unionFor(UnionKey)}
     * and cached in this container for reuse.
     * </p>
     */
    private Unions unions = new Unions();

    /**
     * Clears all cached {@link StarterUnion} instances.
     * <p>
     * After calling this method, all unions will be rebuilt lazily the next time
     * {@link #unionFor(UnionKey)} is invoked.
     * </p>
     */
    public void clearUnions() {
        this.unions = new Unions();
    }

    /**
     * Returns the {@link StarterUnion} for the given {@link UnionKey}.
     * <p>
     * If the union has not been computed yet, it is built from the relevant
     * dictionaries and cached. Subsequent calls with the same key return the
     * cached instance.
     * </p>
     *
     * <p>Union groupings:</p>
     * <ul>
     *   <li><b>S2T / T2S</b> – Simplified ↔ Traditional (with or without punctuation)</li>
     *   <li><b>Taiwan</b> – Phrase-only, variant-only, reverse pairs, and triple unions</li>
     *   <li><b>Hong Kong</b> – Variants-only and reverse pairs</li>
     *   <li><b>Japan</b> – Variants-only and reverse triples</li>
     * </ul>
     *
     * @param key the union key identifying which dictionaries to combine
     * @return the {@link StarterUnion} for the specified key
     * @throws IllegalArgumentException if the key is not recognized
     */
    public StarterUnion unionFor(UnionKey key) {
        switch (key) {
            // --- S2T / T2S ---
            case S2T:
                return getOrInit(unions.s2t,
                        () -> StarterUnion.build(Arrays.asList(st_phrases, st_characters)));
            case S2T_PUNCT:
                return getOrInit(unions.s2t_punct,
                        () -> StarterUnion.build(Arrays.asList(st_phrases, st_characters, st_punctuations)));

            case T2S:
                return getOrInit(unions.t2s,
                        () -> StarterUnion.build(Arrays.asList(ts_phrases, ts_characters)));
            case T2S_PUNCT:
                return getOrInit(unions.t2s_punct,
                        () -> StarterUnion.build(Arrays.asList(ts_phrases, ts_characters, ts_punctuations)));

            // --- TW ---
            case TwPhrasesOnly:
                return getOrInit(unions.tw_phrases_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_phrases)));
            case TwVariantsOnly:
                return getOrInit(unions.tw_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_variants)));
            case TwPhrasesRevOnly:
                return getOrInit(unions.tw_phrases_rev_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_phrases_rev)));
            case TwRevPair:
                return getOrInit(unions.tw_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(tw_variants_rev_phrases, tw_variants_rev)));
            case Tw2SpR1TwRevTriple:
                return getOrInit(unions.tw2sp_r1_tw_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(tw_phrases_rev, tw_variants_rev_phrases, tw_variants_rev)));

            // --- HK ---
            case HkVariantsOnly:
                return getOrInit(unions.hk_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(hk_variants)));
            case HkRevPair:
                return getOrInit(unions.hk_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(hk_variants_rev_phrases, hk_variants_rev)));

            // --- JP ---
            case JpVariantsOnly:
                return getOrInit(unions.jp_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(jp_variants)));
            case JpRevTriple:
                return getOrInit(unions.jp_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(jps_phrases, jps_characters, jp_variants_rev)));

            default:
                throw new IllegalArgumentException("Unhandled UnionKey: " + key);
        }
    }

    // ---- tiny once-initializer for a fixed slot ----

    /**
     * Returns the {@link StarterUnion} stored in the given slot,
     * initializing it if necessary.
     * <p>
     * If the slot is empty, the supplied builder is invoked once to
     * construct a {@link StarterUnion}. The result is stored atomically
     * using {@link AtomicReference#compareAndSet(Object, Object)} to ensure
     * thread safety under concurrent access. If another thread initializes
     * the slot first, its value is returned instead.
     * </p>
     *
     * @param slot  the atomic slot holding the cached {@link StarterUnion}
     * @param build a factory to build the union if the slot is empty
     * @return the cached or newly built {@link StarterUnion}
     * @throws RuntimeException if the builder throws an exception
     */
    private static StarterUnion getOrInit(AtomicReference<StarterUnion> slot,
                                          java.util.concurrent.Callable<StarterUnion> build) {
        StarterUnion v = slot.get();
        if (v != null) return v;
        try {
            StarterUnion built = build.call();
            return slot.compareAndSet(null, built) ? built : slot.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Container for all per-dictionary {@link StarterUnion} slots.
     * <p>
     * Each field corresponds to a specific {@link UnionKey} and is backed
     * by an {@link AtomicReference}, allowing a union to be built once and
     * reused safely across threads. Unions are initialized lazily via
     * {@link #getOrInit(java.util.concurrent.atomic.AtomicReference, java.util.concurrent.Callable)}
     * the first time they are requested by {@link #unionFor(UnionKey)}.
     * </p>
     *
     * <p>
     * Using {@code AtomicReference} avoids synchronization on the entire
     * {@code Unions} container and ensures that each slot can be
     * independently and efficiently initialized under concurrent access.
     * </p>
     */
    static final class Unions {
        // S2T / T2S (+ punct)
        final AtomicReference<StarterUnion> s2t = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2t_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s_punct = new AtomicReference<>();

        // TW
        final AtomicReference<StarterUnion> tw_phrases_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_rev_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw2sp_r1_tw_rev_triple = new AtomicReference<>();

        // HK
        final AtomicReference<StarterUnion> hk_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_rev_pair = new AtomicReference<>();

        // JP
        final AtomicReference<StarterUnion> jp_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_rev_triple = new AtomicReference<>();
    }

}
