package openccjava;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Minimal, schema-specific JSON reader for {@link DictionaryMaxlength} snapshots.
 *
 * <p><strong>Supported schema</strong> (top-level object of named dictionary entries):</p>
 * <pre>{@code
 * {
 *   "st_characters": [ { "汉": "漢", ... }, 2, 1 ],
 *   "st_phrases":    [ { "后台": "後台", ... }, 16, 2 ],
 *   ...
 * }
 * }</pre>
 *
 * <p>Each value is typically an array of three elements:</p>
 * <ol>
 *   <li>a JSON object {@code {string -> string}} for the mapping</li>
 *   <li>a non-negative integer {@code maxLength}</li>
 *   <li>a non-negative integer {@code minLength}</li>
 * </ol>
 *
 * <p>For backward compatibility, older two-element arrays
 * ({@code [ map, maxLength ]}) are also accepted. In that case, {@code minLength}
 * defaults to {@code 1} for non-empty dictionaries and {@code 0} for empty ones.</p>
 *
 * <p><strong>Parser capabilities & limits</strong>:</p>
 * <ul>
 *   <li>Understands objects {@code { ... }}, arrays {@code [ ... ]} (only in the top-level values),
 *       strings (with escapes), and non-negative integers.</li>
 *   <li>Supports the standard string escapes: {@code \" \\ \/ \b \f \n \r \t \\uXXXX}.</li>
 *   <li>Does <em>not</em> support booleans, {@code null}, floating-point numbers, comments, or
 *       arbitrary nested arrays/objects outside the specified shape.</li>
 * </ul>
 *
 * <p><strong>Error handling</strong>:
 * the parser throws {@link IllegalArgumentException} on malformed input with a short context window
 * around the error location.</p>
 *
 * <p><strong>Thread-safety</strong>:
 * instances are not shared; all state is confined to a single parse call.</p>
 *
 * <p>Intended use: load a snapshot produced by Jackson (or your serializer)
 * <em>without</em> bringing Jackson as a dependency.</p>
 */
final class MiniDictJson {
    /**
     * Entire JSON buffer under parse.
     */
    private final String s;
    /**
     * Cursor into {@link #s}.
     */
    private int i = 0;

    /**
     * Creates a new parser over the given JSON buffer.
     * <p>Private: use one of the {@code parseToMap(...)} factory methods instead.</p>
     *
     * @param s complete JSON document to parse
     */
    private MiniDictJson(String s) {
        this.s = s;
    }

    /**
     * Parses a JSON snapshot from a filesystem {@link Path}.
     *
     * @param path path to a UTF-8 JSON file
     * @return a map from top-level key to {@link DictionaryMaxlength.DictEntry}
     * @throws IOException              if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    static Map<String, DictionaryMaxlength.DictEntry> parseToMap(Path path) throws IOException {
        String jsonString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return parseToMap(jsonString);
    }

    /**
     * Parses a JSON snapshot from an {@link InputStream}.
     *
     * <p>The stream is read as UTF-8 and fully buffered into memory before parsing.</p>
     *
     * @param in input stream containing UTF-8 JSON
     * @return a map from top-level key to {@link DictionaryMaxlength.DictEntry}
     * @throws IOException              if an I/O error occurs while reading the stream
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    static Map<String, DictionaryMaxlength.DictEntry> parseToMap(InputStream in) throws IOException {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder b = new StringBuilder(1 << 20);
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) b.append(buf, 0, n);
            return parseToMap(b.toString());
        }
    }

    /**
     * Parses a JSON snapshot from a {@link String}.
     *
     * @param json complete JSON document
     * @return a map from top-level key to {@link DictionaryMaxlength.DictEntry}
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    static Map<String, DictionaryMaxlength.DictEntry> parseToMap(String json) {
        MiniDictJson p = new MiniDictJson(json);
        p.skipWs();
        p.expect('{');

        Map<String, DictionaryMaxlength.DictEntry> out = new LinkedHashMap<>();

        p.skipWs();
        if (p.peek('}')) {
            p.i++;
            p.skipWs();
            p.ensureEof();
            return out;
        }

        while (true) {
            p.skipWs();
            String key = p.readString();
            p.skipWs();
            p.expect(':');
            p.skipWs();

            // Expect: [ { ... }, int ] or [ { ... }, int, int ]
            DictionaryMaxlength.DictEntry entry = p.readDictEntryArray();
            out.put(key, entry);

            p.skipWs();
            if (p.peek(',')) {
                p.i++;
                continue;
            }
            if (p.peek('}')) {
                p.i++;
                break;
            }
            throw p.err("Expected ',' or '}' after top-level entry");
        }
        p.skipWs();
        p.ensureEof();
        return out;
    }

    // ---- Core readers -------------------------------------------------------

    /**
     * Reads an array of the form {@code [ {string->string}, maxLength ]} or
     * {@code [ {string->string}, maxLength, minLength ]}.
     *
     * @return a {@link DictionaryMaxlength.DictEntry} constructed from the array contents
     * @throws IllegalArgumentException if the array does not match the expected shape
     */
    private DictionaryMaxlength.DictEntry readDictEntryArray() {
        expect('[');
        skipWs();

        Map<String, String> map = readStringMap();
        skipWs();
        expect(',');
        skipWs();

        int maxLen = readInt();
        skipWs();

        final int minLen;
        if (peek(']')) {
            minLen = map.isEmpty() ? 0 : 1;
        } else {
            expect(',');
            skipWs();
            minLen = readInt();
        }
        skipWs();
        expect(']');

        if (maxLen < 0) throw err("maxLength must be >= 0");
        if (minLen < 0) throw err("minLength must be >= 0");
        if (maxLen > 0 && minLen > maxLen) throw err("minLength cannot exceed maxLength");

        return new DictionaryMaxlength.DictEntry(map, maxLen, minLen);
    }

    /**
     * Reads a JSON object of the form {@code { "k":"v", ... }} where both keys
     * and values are strings. Supports standard string escapes and Unicode escapes.
     *
     * @return a {@code Map<String,String>} with insertion order preserved
     * @throws IllegalArgumentException if the object is malformed
     */
    private Map<String, String> readStringMap() {
        expect('{');
        skipWs();
        Map<String, String> m = new LinkedHashMap<>();
        if (peek('}')) {
            i++;
            return m;
        }

        while (true) {
            String k = readString();
            skipWs();
            expect(':');
            skipWs();
            String v = readString();
            m.put(k, v);

            skipWs();
            if (peek(',')) {
                i++;
                skipWs();
                continue;
            }
            if (peek('}')) {
                i++;
                break;
            }
            throw err("Expected ',' or '}' in object");
        }
        return m;
    }

    /**
     * Reads a JSON string token, decoding escape sequences including {@code \\uXXXX}.
     *
     * @return the decoded Java {@link String}
     * @throws IllegalArgumentException if the string is unterminated or contains bad escapes
     */
    private String readString() {
        expect('"');
        StringBuilder b = new StringBuilder(32);
        while (true) {
            if (eof()) throw err("Unterminated string");
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                if (eof()) throw err("Bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        b.append(e);
                        break;
                    case 'b':
                        b.append('\b');
                        break;
                    case 'f':
                        b.append('\f');
                        break;
                    case 'n':
                        b.append('\n');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw err("Incomplete \\uXXXX escape");
                        }
                        int code = hex4(s.charAt(i), s.charAt(i + 1), s.charAt(i + 2), s.charAt(i + 3));
                        i += 4;
                        b.append((char) code);
                        break;
                    default:
                        throw err("Bad escape: \\" + e);
                }

            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Reads a non-negative integer.
     *
     * @return the parsed integer value
     * @throws IllegalArgumentException if no digits are present or the number overflows {@link Integer}
     */
    private int readInt() {
        if (!has() || !Character.isDigit(s.charAt(i))) throw err("Expected integer");
        int start = i;
        while (has() && Character.isDigit(s.charAt(i))) i++;
        try {
            return Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException ex) {
            throw err("Invalid integer");
        }
    }

    // ---- Utils --------------------------------------------------------------

    /**
     * Skips ASCII whitespace characters: space, tab, CR, LF.
     */
    private void skipWs() {
        while (has()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
                continue;
            }
            break;
        }
    }

    /**
     * Consumes the expected character or throws.
     *
     * @param c the expected character
     * @throws IllegalArgumentException if the next character does not match
     */
    private void expect(char c) {
        if (!has() || s.charAt(i) != c) throw err("Expected '" + c + "'");
        i++;
    }

    /**
     * @return whether the next character equals {@code c} (does not consume)
     */
    private boolean peek(char c) {
        return has() && s.charAt(i) == c;
    }

    /**
     * @return {@code true} if {@link #i} is within {@link #s}
     */
    private boolean has() {
        return i < s.length();
    }

    /**
     * @return {@code true} if the parser has reached the end of the buffer
     */
    private boolean eof() {
        return i >= s.length();
    }

    /**
     * Ensures no trailing non-whitespace data remains.
     *
     * @throws IllegalArgumentException if trailing data exists
     */
    private void ensureEof() {
        if (!eof()) throw err("Trailing data");
    }

    /**
     * Parses four hex digits to a 16-bit code unit.
     */
    private static int hex4(char a, char b, char c, char d) {
        return (hex(a) << 12) | (hex(b) << 8) | (hex(c) << 4) | hex(d);
    }

    /**
     * Parses a single hex digit.
     *
     * @throws IllegalArgumentException if {@code ch} is not 0–9, a–f, or A–F
     */
    private static int hex(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return 10 + (ch - 'a');
        if (ch >= 'A' && ch <= 'F') return 10 + (ch - 'A');
        throw new IllegalArgumentException("Bad hex digit: " + ch);
    }

    /**
     * Builds an {@link IllegalArgumentException} with a small context window
     * around the current parse position to aid debugging malformed JSON.
     */
    private IllegalArgumentException err(String msg) {
        int from = Math.max(0, i - 16);
        int to = Math.min(s.length(), i + 16);
        String ctx = s.substring(from, to).replace("\n", "\\n");
        return new IllegalArgumentException(msg + " at pos " + i + " near: \"" + ctx + "\"");
    }
}
