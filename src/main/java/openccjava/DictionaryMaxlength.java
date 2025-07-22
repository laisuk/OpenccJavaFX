package openccjava;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

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
            this.dict = dict;
            this.maxLength = maxLength;
        }
    }

    // Dictionary fields (populated via JSON or fallback loading)
    public DictEntry st_characters;
    public DictEntry st_phrases;
    public DictEntry ts_characters;
    public DictEntry ts_phrases;
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
        DictionaryMaxlength instance = new DictionaryMaxlength();

        Map<String, String> paths = Map.ofEntries(
                Map.entry("st_characters", "STCharacters.txt"),
                Map.entry("st_phrases", "STPhrases.txt"),
                Map.entry("ts_characters", "TSCharacters.txt"),
                Map.entry("ts_phrases", "TSPhrases.txt"),
                Map.entry("tw_phrases", "TWPhrases.txt"),
                Map.entry("tw_phrases_rev", "TWPhrasesRev.txt"),
                Map.entry("tw_variants", "TWVariants.txt"),
                Map.entry("tw_variants_rev", "TWVariantsRev.txt"),
                Map.entry("tw_variants_rev_phrases", "TWVariantsRevPhrases.txt"),
                Map.entry("hk_variants", "HKVariants.txt"),
                Map.entry("hk_variants_rev", "HKVariantsRev.txt"),
                Map.entry("hk_variants_rev_phrases", "HKVariantsRevPhrases.txt"),
                Map.entry("jps_characters", "JPShinjitaiCharacters.txt"),
                Map.entry("jps_phrases", "JPShinjitaiPhrases.txt"),
                Map.entry("jp_variants", "JPVariants.txt"),
                Map.entry("jp_variants_rev", "JPVariantsRev.txt")
        );

        for (Map.Entry<String, String> entry : paths.entrySet()) {
            try {
                String content = loadDictFile(basePath, entry.getValue());
                DictEntry loaded = loadDictionaryMaxlength(content);
                Field field = DictionaryMaxlength.class.getField(entry.getKey());
                field.set(instance, loaded);
            } catch (IOException | NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Error loading dict: " + entry.getKey(), e);
            }
        }

        return instance;
    }

    /**
     * Loads the dictionary file content from a specified base directory or classpath.
     *
     * @param basePath the base directory path (e.g., "dicts" or "custom_dicts")
     * @param filename the dictionary filename (e.g. "STCharacters.txt")
     * @return the dictionary file content as UTF-8 string
     * @throws IOException if the file is not found or unreadable
     */
    private static String loadDictFile(String basePath, String filename) throws IOException {
        Path filePath = Paths.get(basePath, filename);
        if (Files.exists(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }

        try (InputStream in = DictionaryMaxlength.class.getResourceAsStream("/" + basePath + "/" + filename)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        throw new FileNotFoundException("Dictionary not found in " + basePath + ": " + filename);
    }

    /**
     * Parses the content of a dictionary text file into a {@code DictEntry}.
     *
     * <p>Each line must have at least two whitespace-separated parts: source phrase and its translation.
     *
     * @param content raw text content from a dictionary file
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     */
    public static DictEntry loadDictionaryMaxlength(String content) {
        Map<String, String> dict = new HashMap<>();
        int maxLength = 1;

        for (String line : content.strip().split("\\R")) {
            String[] parts = line.strip().split("\\s+");
            if (parts.length >= 2) {
                String phrase = parts[0];
                String translation = parts[1];
                dict.put(phrase, translation);
                maxLength = Math.max(maxLength, phrase.length());
            } else {
                System.err.println("Warning: malformed line ignored: " + line);
            }
        }

        return new DictEntry(dict, maxLength);
    }

    /**
     * Serializes this {@code DictionaryMaxlength} object to a JSON file.
     *
     * @param outputPath the output path where the JSON should be written
     * @throws RuntimeException if writing the file fails
     */
    public void serializeToJson(String outputPath) {
        ObjectMapper mapper = new ObjectMapper();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to: " + outputPath, e);
        }
    }
}
