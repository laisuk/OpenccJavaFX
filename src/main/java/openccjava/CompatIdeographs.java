package openccjava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static openccjava.Utils.readUtf8;

/**
 * Utility methods for normalizing CJK Compatibility Ideographs to their
 * canonical CJK Unified Ideograph forms.
 *
 * <p>This class is independent of the normal OpenCC conversion pipeline. It
 * does not change dictionaries, regional phrase conversion, punctuation
 * conversion, script detection, or segmentation behavior. Apply it as a
 * pre-processing step before OpenCC conversion when input may contain CJK
 * Compatibility Ideographs such as {@code 金}.</p>
 *
 * <p>The built-in table is loaded from the classpath resource
 * {@code /dicts/CJK_Compatibility_Ideographs.txt}. If the resource is
 * unavailable or cannot be read, the built-in map is empty and normalization
 * preserves the input text.</p>
 *
 * <p>Mappings are processed as Unicode code points rather than UTF-16
 * {@code char} values, so supplementary-plane compatibility ideographs are
 * supported correctly.</p>
 *
 * <p>This class cannot be instantiated.</p>
 *
 * @since 1.4.1
 */
public final class CompatIdeographs {
    private static final String BUILTIN_RESOURCE = "/dicts/CJK_Compatibility_Ideographs.txt";

    private static final Map BUILTIN = loadBuiltinMap();

    private CompatIdeographs() {
    }

    /**
     * Normalizes text using the built-in CJK Compatibility Ideographs table.
     *
     * <p>Only characters present in the table are replaced. Unmapped characters
     * are preserved. Supplementary characters are processed as full Unicode code
     * points rather than isolated UTF-16 surrogate values.</p>
     *
     * @param input the input text; {@code null} and empty strings return
     *              {@code ""}
     * @return text with mapped compatibility ideographs normalized
     */
    public static String normalize(String input) {
        return BUILTIN.normalize(input);
    }

    /**
     * Normalizes one Unicode scalar value using the built-in table.
     *
     * @param scalar a string containing exactly one Unicode code point
     * @return the normalized scalar, or the original scalar when unmapped
     * @throws NullPointerException     if {@code scalar} is {@code null}
     * @throws IllegalArgumentException if {@code scalar} does not contain
     *                                  exactly one Unicode code point
     */
    public static String normalizeScalar(String scalar) {
        return BUILTIN.normalizeScalar(scalar);
    }

    /**
     * Normalizes the contents of a {@link StringBuilder} in place using the
     * built-in CJK Compatibility Ideographs table.
     *
     * @param builder the builder to normalize
     * @return the same builder instance
     * @throws NullPointerException if {@code builder} is {@code null}
     */
    public static StringBuilder normalizeInPlace(StringBuilder builder) {
        if (builder == null)
            throw new NullPointerException("builder");

        String normalized = normalize(builder.toString());
        builder.setLength(0);
        builder.append(normalized);
        return builder;
    }

    /**
     * Creates a mutable compatibility-ideograph map populated with the built-in
     * Unicode mappings.
     *
     * @return a new mutable map containing the built-in mappings
     */
    public static Map builtinMap() {
        return new Map(new HashMap<>(BUILTIN.map));
    }

    /**
     * Creates a mutable compatibility-ideograph map from UTF-8 table text.
     *
     * <p>The file format is one mapping per line:</p>
     *
     * <pre>{@code
     * compatibility_ideograph<TAB>unified_ideograph
     * }</pre>
     *
     * <p>Blank lines and lines beginning with {@code #} are ignored. Each
     * mapping side must contain exactly one Unicode code point.</p>
     *
     * @param text UTF-8 table text
     * @return a new mutable map containing the parsed mappings
     * @throws NullPointerException     if {@code text} is {@code null}
     * @throws IllegalArgumentException if a non-empty, non-comment line is
     *                                  malformed
     */
    public static Map fromText(String text) {
        if (text == null)
            throw new NullPointerException("text");

        return new Map(parseMap(text, true));
    }

    private static Map loadBuiltinMap() {
        InputStream stream = CompatIdeographs.class.getResourceAsStream(BUILTIN_RESOURCE);
        if (stream == null)
            return Map.empty();

        try {
            return new Map(parseMap(readUtf8(stream), false));
        } catch (IOException | RuntimeException e) {
            return Map.empty();
        }
    }

    private static HashMap<Integer, Integer> parseMap(String text, boolean strict) {
        HashMap<Integer, Integer> map = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    if (strict)
                        throw malformed(lineNumber, line);
                    continue;
                }

                Integer source = tryReadSingleCodePoint(parts[0].trim());
                Integer replacement = tryReadSingleCodePoint(parts[1].trim());

                if (source == null || replacement == null) {
                    if (strict)
                        throw malformed(lineNumber, line);
                    continue;
                }

                map.put(source, replacement);
            }
        } catch (IOException ignored) {
            // StringReader should not throw in normal use.
        }

        return map;
    }

    private static IllegalArgumentException malformed(int lineNumber, String line) {
        return new IllegalArgumentException(
                "Malformed CJK compatibility ideograph mapping at line " + lineNumber + ": " + line
        );
    }

    private static Integer tryReadSingleCodePoint(String value) {
        if (value == null || value.isEmpty())
            return null;

        int first = value.codePointAt(0);
        int charCount = Character.charCount(first);

        if (charCount != value.length())
            return null;

        return first;
    }

    private static String singleCodePointToString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /**
     * Mutable CJK Compatibility Ideographs normalization map.
     *
     * <p>Instances are mutable and are not synchronized. If a map is shared
     * across threads, callers should finish configuring it before sharing or
     * provide their own synchronization.</p>
     */
    public static final class Map {
        private final HashMap<Integer, Integer> map;

        private Map(HashMap<Integer, Integer> map) {
            this.map = map;
        }

        private static Map empty() {
            return new Map(new HashMap<>());
        }

        /**
         * Adds custom mappings from UTF-8 table text and returns this map.
         *
         * @param text UTF-8 mapping table text
         * @return this map, after adding parsed mappings
         * @throws NullPointerException     if {@code text} is {@code null}
         * @throws IllegalArgumentException if a non-empty, non-comment line is
         *                                  malformed
         */
        public Map withText(String text) {
            if (text == null)
                throw new NullPointerException("text");

            map.putAll(parseMap(text, true));
            return this;
        }

        /**
         * Adds custom mappings from a UTF-8 text file and returns this map.
         *
         * @param path path to the UTF-8 custom mapping file
         * @return this map, after adding parsed mappings
         * @throws IOException              if the file cannot be read
         * @throws NullPointerException     if {@code path} is {@code null}
         * @throws IllegalArgumentException if the file contains malformed
         *                                  mapping lines
         */
        public Map withCustomFile(String path) throws IOException {
            if (path == null)
                throw new NullPointerException("path");

            String text = new String(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    StandardCharsets.UTF_8
            );

            return withText(text);
        }

        /**
         * Adds custom in-memory mappings and returns this map.
         *
         * <p>Each key and value must contain exactly one Unicode code point.
         * Entries with {@code null}, empty, or multi-code-point keys or values
         * are ignored.</p>
         *
         * @param pairs custom compatibility ideograph to unified ideograph
         *              mappings
         * @return this map, after adding valid custom pairs
         * @throws NullPointerException if {@code pairs} is {@code null}
         */
        public Map withCustomPairs(java.util.Map<String, String> pairs) {
            if (pairs == null)
                throw new NullPointerException("pairs");

            for (java.util.Map.Entry<String, String> pair : pairs.entrySet()) {
                Integer source = tryReadSingleCodePoint(pair.getKey());
                Integer replacement = tryReadSingleCodePoint(pair.getValue());

                if (source != null && replacement != null)
                    map.put(source, replacement);
            }

            return this;
        }

        /**
         * Normalizes text using this map.
         *
         * @param input the input text; {@code null} and empty strings return
         *              {@code ""}
         * @return text with mapped compatibility ideographs normalized
         */
        public String normalize(String input) {
            if (input == null || input.isEmpty())
                return "";

            StringBuilder sb = new StringBuilder(input.length());

            for (int i = 0; i < input.length(); ) {
                int cp = input.codePointAt(i);
                i += Character.charCount(cp);

                Integer replacement = map.get(cp);
                sb.appendCodePoint(replacement != null ? replacement : cp);
            }

            return sb.toString();
        }

        /**
         * Normalizes one Unicode scalar value using this map.
         *
         * @param scalar a string containing exactly one Unicode code point
         * @return the normalized scalar, or the original scalar when unmapped
         * @throws NullPointerException     if {@code scalar} is {@code null}
         * @throws IllegalArgumentException if {@code scalar} does not contain
         *                                  exactly one Unicode code point
         */
        public String normalizeScalar(String scalar) {
            if (scalar == null)
                throw new NullPointerException("scalar");

            Integer source = tryReadSingleCodePoint(scalar);
            if (source == null)
                throw new IllegalArgumentException("scalar must contain exactly one Unicode code point.");

            Integer replacement = map.get(source);
            return singleCodePointToString(replacement != null ? replacement : source);
        }
    }
}
