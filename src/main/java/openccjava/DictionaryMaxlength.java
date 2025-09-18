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
 */
public class DictionaryMaxlength {
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
         * Constructs an empty dictionary entry
         */
        public DictEntry() {
            this.dict = new HashMap<>();
            this.maxLength = 0;
        }

        /**
         * Constructs a dictionary entry with data and computed max length
         */
        public DictEntry(Map<String, String> dict, int maxLength) {
            this.dict = new HashMap<>(dict);
            this.maxLength = maxLength;
        }
    }

    // Dictionary fields (populated via JSON or fallback loading)
    public DictEntry st_characters;
    public DictEntry st_phrases;
    public DictEntry st_punctuations;
    public DictEntry ts_characters;
    public DictEntry ts_phrases;
    public DictEntry ts_punctuations;
    public DictEntry tw_phrases;
    public DictEntry tw_phrases_rev;
    public DictEntry tw_variants;
    public DictEntry tw_variants_rev;
    public DictEntry tw_variants_rev_phrases;
    public DictEntry hk_variants;
    public DictEntry hk_variants_rev;
    public DictEntry hk_variants_rev_phrases;
    public DictEntry jps_characters;
    public DictEntry jps_phrases;
    public DictEntry jp_variants;
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
                        if (in == null) throw new FileNotFoundException(filename);
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

    // --- No-reflection field assignment table (Java 8 compatible) ---
    private static final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> ASSIGN;

    static {
        Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> m = new LinkedHashMap<>();

        m.put("st_characters", (o, e) -> o.st_characters = e);
        m.put("st_phrases", (o, e) -> o.st_phrases = e);
        m.put("ts_characters", (o, e) -> o.ts_characters = e);
        m.put("ts_phrases", (o, e) -> o.ts_phrases = e);
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
     * Returns the assignment table mapping dictionary identifiers to setter functions.
     * <p>
     * Each entry in the map associates a string key (e.g., {@code "st_characters"})
     * with a {@link BiConsumer} that, when invoked, assigns a loaded {@link DictEntry}
     * to the corresponding field of a {@link DictionaryMaxlength} instance.
     * </p>
     * <p>
     * The returned map preserves insertion order and is unmodifiable.
     * Attempting to modify it will result in an {@link UnsupportedOperationException}.
     * </p>
     *
     * @return an unmodifiable map of dictionary field setters keyed by dictionary name
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
            m.put("ts_characters", "TSCharacters.txt");
            m.put("ts_phrases", "TSPhrases.txt");
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
     * Parses the content of a dictionary text file into a {@code DictEntry}.
     *
     * <p>Expected format per line:
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
        int lineNo = 0;

        for (String raw; (raw = br.readLine()) != null; ) {
            lineNo++;
//            String line = raw.strip();
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            int tab = line.indexOf('\t');
            if (tab < 0) {
                System.err.println("Warning: malformed (no TAB) at line " + lineNo + ": " + raw);
                continue;
            }

            String key = line.substring(0, tab);
            if (lineNo == 1 && !key.isEmpty() && key.charAt(0) == '\uFEFF') key = key.substring(1);

            // first token after TAB (space OR tab ends it)
            String val = getRestString(line, tab);

            if (key.isEmpty() || val.isEmpty()) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }

            dict.put(key, val);
            if (key.length() > maxLength) maxLength = key.length(); // UTF-16 length (keep non-BMA as 2)
        }
        return new DictEntry(dict, maxLength);
    }

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
    private Unions unions = new Unions();

    /**
     * Clear all cached unions (rebuilds lazily on next request).
     */
    public void clearUnions() {
        this.unions = new Unions();
    }

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
     * Internal per-dictionary union slots (once-built, reused).
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
