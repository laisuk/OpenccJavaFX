package openccjava;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class DictionaryMaxlength {

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class DictEntry {
        public Map<String, String> dict;
        public int maxLength;

        public DictEntry() {
            this.dict = new HashMap<>();
            this.maxLength = 0;
        }

        public DictEntry(Map<String, String> dict, int maxLength) {
            this.dict = dict;
            this.maxLength = maxLength;
        }
    }

    // Dictionary fields
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

    public static DictionaryMaxlength fromJson(File jsonFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonFile, DictionaryMaxlength.class);
    }

    public static DictionaryMaxlength fromJson(String path) throws IOException {
        return fromJson(new File(path));
    }

    public static DictionaryMaxlength fromDicts() {
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

        Path base = Paths.get("dicts");

        for (Map.Entry<String, String> entry : paths.entrySet()) {
            try {
                String content = Files.readString(base.resolve(entry.getValue()), StandardCharsets.UTF_8);
                DictEntry loaded = loadDictionaryMaxlength(content);
                Field field = DictionaryMaxlength.class.getField(entry.getKey());
                field.set(instance, loaded);
            } catch (IOException | NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Error loading dict: " + entry.getKey(), e);
            }
        }

        return instance;
    }

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

    public void serializeToJson(String outputPath) {
        ObjectMapper mapper = new ObjectMapper();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to: " + outputPath, e);
        }
    }
}
