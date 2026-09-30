package openccjava;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

//import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DictionaryMaxlengthNoDepsTest {

    // v2 schema: [ {dict}, maxLength, minLength ]
    private static final String SAMPLE_JSON =
            "{\n" +
                    "  \"st_characters\": [ { \"汉\": \"漢\", \"发\": \"發\" }, 1, 1 ],\n" +
                    "  \"st_phrases\":    [ { \"后台\": \"後台\" }, 2, 2 ],\n" +
                    "  \"tw_variants_phrases\": [ { \"喫茶小舖\": \"喫茶小舖\" }, 4, 4 ],\n" +
                    "  \"hk_variants_phrases\": [ { \"喫茶小舖\": \"喫茶小舖\" }, 4, 4 ],\n" +
                    "  \"jps_characters\":[ { \"芸\": \"藝\" }, 1, 1 ],\n" +
                    "  \"unknown_block\": [ { \"x\": \"y\" }, 9, 1 ]\n" +
                    "}";

    private static final String RESOURCE_PATH = "/dicts/dictionary_maxlength.json";

    @Test
    void loadsFromStringNoDeps() {
        DictionaryMaxlength d = DictionaryMaxlength.fromJsonStringNoDeps(SAMPLE_JSON);

        assertNotNull(d.st_characters);
        assertEquals(1, d.st_characters.maxLength);
        assertEquals(1, d.st_characters.minLength);
        Map<String, String> expectedStChars = new LinkedHashMap<>();
        expectedStChars.put("汉", "漢");
        expectedStChars.put("发", "發");
        assertEquals(expectedStChars, d.st_characters.dict);

        assertNotNull(d.st_phrases);
        assertEquals(2, d.st_phrases.maxLength);
        assertEquals(2, d.st_phrases.minLength);
        assertEquals(Collections.singletonMap("后台", "後台"), d.st_phrases.dict);

        assertNotNull(d.jps_characters);
        assertEquals(1, d.jps_characters.maxLength);
        assertEquals(1, d.jps_characters.minLength);
        assertEquals(Collections.singletonMap("芸", "藝"), d.jps_characters.dict);

        assertEquals("喫茶小舖", d.tw_variants_phrases.dict.get("喫茶小舖"));
        assertEquals("喫茶小舖", d.hk_variants_phrases.dict.get("喫茶小舖"));

        // Unknown key ignored — no assertion needed for unknown_block
    }

    @Test
    void loadsFromInputStreamNoDeps() throws Exception {
        byte[] bytes = SAMPLE_JSON.getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(in);
            assertEquals("漢", d.st_characters.dict.get("汉"));
            assertEquals(2, d.st_phrases.maxLength);
        }
    }

    @Test
    void loadsFromPathNoDeps(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("dictionary_maxlength.json");
//        Files.writeString(json, SAMPLE_JSON, StandardCharsets.UTF_8);
        Files.write(json, SAMPLE_JSON.getBytes(StandardCharsets.UTF_8));

        DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(json);

        assertEquals("後台", d.st_phrases.dict.get("后台"));
        assertEquals(1, d.jps_characters.maxLength);
        assertNull(d.jps_characters_rev); // not provided in sample JSON
    }

    @Test
    void loadsRealSnapshotFromClasspath() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/dicts/dictionary_maxlength.json")) {
            assertNotNull(in, "Test resource dicts/dictionary_maxlength.json is missing");
            DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(in);

            // Sanity checks on a few well-known buckets
            assertNotNull(d.st_characters);
            assertTrue(d.st_characters.maxLength > 0);
            assertFalse(d.st_characters.dict.isEmpty());

            assertNotNull(d.st_phrases);
            assertTrue(d.st_phrases.maxLength > 1);
            assertFalse(d.st_phrases.dict.isEmpty());

            assertNotNull(d.jps_characters);
            assertFalse(d.jps_characters.dict.isEmpty());

            assertNotNull(d.tw_variants_phrases);
            assertNotNull(d.hk_variants_phrases);

            // Spot-check a known mapping if you’re comfortable hard-coding it:
            assertEquals("後", d.st_characters.dict.get("后"));
        }
    }

    @Test
    void roundTrip_viaClasspath(@TempDir Path tmp) throws Exception {
        // 1) Load real snapshot using the no-deps parser
        byte[] original;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Missing test resource " + RESOURCE_PATH);

            // Java 8 replacement for InputStream.readAllBytes()
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmpBuf = new byte[8192];
            int n;
            while ((n = in.read(tmpBuf)) != -1) {
                buffer.write(tmpBuf, 0, n);
            }
            original = buffer.toByteArray();
        }

        DictionaryMaxlength a = DictionaryMaxlength.fromJsonNoDeps(new ByteArrayInputStream(original));
        // 2) Serialize back to JSON
        Path out = tmp.resolve("rt.json");
        a.serializeToJsonNoDeps(out);
        // 3) Load the emitted JSON again with the no-deps parser
        DictionaryMaxlength b = DictionaryMaxlength.fromJsonNoDeps(out);
        // 4) Deep-compare all dict entries
        assertDictionariesEqual(a, b);
    }

    @Test
    void roundTrip_viaString() throws Exception {
        // Load via no-deps
        String json;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Missing test resource " + RESOURCE_PATH);

            // Java 8 replacement for InputStream.readAllBytes()
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmpBuf = new byte[8192];
            int n;
            while ((n = in.read(tmpBuf)) != -1) {
                buffer.write(tmpBuf, 0, n);
            }

            json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }

        DictionaryMaxlength a = DictionaryMaxlength.fromJsonStringNoDeps(json);
        // Serialize to a String
        String emitted = a.serializeToJsonStringNoDepsCompact();
        // Reload via no-deps
        DictionaryMaxlength b = DictionaryMaxlength.fromJsonStringNoDeps(emitted);
        // Compare
        assertDictionariesEqual(a, b);
    }

    @Test
    void loadsFromStringNoDeps_usesHelpers() {
        DictionaryMaxlength actual = DictionaryMaxlength.fromJsonStringNoDeps(SAMPLE_JSON);

        // Build expected with only the blocks present in SAMPLE_JSON
        DictionaryMaxlength expected = new DictionaryMaxlength();

        Map<String, String> stChars = new LinkedHashMap<>();
        stChars.put("汉", "漢");
        stChars.put("发", "發");
        expected.st_characters = new DictionaryMaxlength.DictEntry(stChars, 1, 1);

        Map<String, String> stPhrases = new LinkedHashMap<>();
        stPhrases.put("后台", "後台");
        expected.st_phrases = new DictionaryMaxlength.DictEntry(stPhrases, 2, 2);

        Map<String, String> twVariantPhrases = new LinkedHashMap<>();
        twVariantPhrases.put("喫茶小舖", "喫茶小舖");
        expected.tw_variants_phrases = new DictionaryMaxlength.DictEntry(twVariantPhrases, 4, 4);

        Map<String, String> hkVariantPhrases = new LinkedHashMap<>();
        hkVariantPhrases.put("喫茶小舖", "喫茶小舖");
        expected.hk_variants_phrases = new DictionaryMaxlength.DictEntry(hkVariantPhrases, 4, 4);

        Map<String, String> jpsChars = new LinkedHashMap<>();
        jpsChars.put("芸", "藝");
        expected.jps_characters = new DictionaryMaxlength.DictEntry();
        expected.jps_characters.dict = jpsChars;
        expected.jps_characters.maxLength = 1;
        expected.jps_characters.minLength = 1;

        expected.hk_phrases = new DictionaryMaxlength.DictEntry();
        expected.hk_phrases_rev = new DictionaryMaxlength.DictEntry();

        // All other fields remain null in both expected and actual.
        assertDictionariesEqual(expected, actual);
    }

    @Test
    void olderJsonMissingDirectHongKongPhraseFieldsUsesEmptyEntries() {
        String olderJson = "{ \"st_characters\": [ { \"汉\": \"漢\" }, 1, 1 ] }";

        DictionaryMaxlength d = DictionaryMaxlength.fromJsonStringNoDeps(olderJson);

        assertNotNull(d.hk_phrases);
        assertTrue(d.hk_phrases.dict.isEmpty());
        assertNotNull(d.hk_phrases_rev);
        assertTrue(d.hk_phrases_rev.dict.isEmpty());
    }

    @Test
    void roundTripSerializeAndParseNoDeps_withHelpers() throws IOException {
        DictionaryMaxlength d1 = DictionaryMaxlength.fromJsonStringNoDeps(SAMPLE_JSON);
        String jsonOut = d1.serializeToJsonStringNoDeps();   // writer always emits minLength now
        DictionaryMaxlength d2 = DictionaryMaxlength.fromJsonStringNoDeps(jsonOut);

        assertDictionariesEqual(d1, d2);
    }

    @Test
    void acceptsV1SchemaWithoutMinLength() {
        String v1 = "{ \"st_characters\": [ { \"汉\": \"漢\" }, 1 ] }";

        DictionaryMaxlength d = DictionaryMaxlength.fromJsonStringNoDeps(v1);

        assertNotNull(d.st_characters);
        assertEquals(1, d.st_characters.maxLength);
        assertEquals(1, d.st_characters.minLength);
        assertEquals("漢", d.st_characters.dict.get("汉"));
    }

    private static void assertDictionariesEqual(DictionaryMaxlength a, DictionaryMaxlength b) {
        // compare each DictEntry field (no reflection in production code; tests are fine being explicit)
        compare("st_characters", a.st_characters, b.st_characters);
        compare("st_phrases", a.st_phrases, b.st_phrases);
        compare("st_punctuations", a.st_punctuations, b.st_punctuations);

        compare("ts_characters", a.ts_characters, b.ts_characters);
        compare("ts_phrases", a.ts_phrases, b.ts_phrases);
        compare("ts_punctuations", a.ts_punctuations, b.ts_punctuations);

        compare("tw_phrases", a.tw_phrases, b.tw_phrases);
        compare("tw_phrases_rev", a.tw_phrases_rev, b.tw_phrases_rev);
        compare("tw_variants", a.tw_variants, b.tw_variants);
        compare("tw_variants_phrases", a.tw_variants_phrases, b.tw_variants_phrases);
        compare("tw_variants_rev", a.tw_variants_rev, b.tw_variants_rev);
        compare("tw_variants_rev_phrases", a.tw_variants_rev_phrases, b.tw_variants_rev_phrases);

        compare("hk_phrases", a.hk_phrases, b.hk_phrases);
        compare("hk_phrases_rev", a.hk_phrases_rev, b.hk_phrases_rev);
        compare("hk_variants", a.hk_variants, b.hk_variants);
        compare("hk_variants_phrases", a.hk_variants_phrases, b.hk_variants_phrases);
        compare("hk_variants_rev", a.hk_variants_rev, b.hk_variants_rev);
        compare("hk_variants_rev_phrases", a.hk_variants_rev_phrases, b.hk_variants_rev_phrases);

        compare("jps_characters", a.jps_characters, b.jps_characters);
        compare("jps_characters_rev", a.jps_characters_rev, b.jps_characters_rev);
        compare("jps_phrases", a.jps_phrases, b.jps_phrases);
    }

    private static void compare(String name, DictionaryMaxlength.DictEntry x, DictionaryMaxlength.DictEntry y) {
        if (x == null && y == null) return;
        assertNotNull(x, name + " is null on left but not on right");
        assertNotNull(y, name + " is null on right but not on left");
        assertEquals(x.maxLength, y.maxLength, name + ".maxLength");
        assertEquals(x.minLength, y.minLength, name + ".minLength"); // NEW
        assertEquals(x.dict, y.dict, name + ".dict");
    }
}
