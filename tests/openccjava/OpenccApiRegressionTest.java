package openccjava;

import openccjavacli.DictgenCommand;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenccApiRegressionTest {
    @Test
    void fromJsonAcceptsLegacyTwoElementDictEntries() throws Exception {
        String json = "{\"st_characters\":[{\"汉\":\"漢\"},1]}";

        DictionaryMaxlength dictionary = DictionaryMaxlength.fromJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );

        assertNotNull(dictionary.st_characters);
        assertEquals("漢", dictionary.st_characters.dict.get("汉"));
        assertEquals(1, dictionary.st_characters.maxLength);
        assertEquals(1, dictionary.st_characters.minLength);
    }

    @Test
    void fromJsonAcceptsForwardRegionalVariantPhraseSlots() throws Exception {
        String json = "{"
                + "\"tw_variants_phrases\":[{\"喫茶小舖\":\"喫茶小舖\"},4,4],"
                + "\"hk_variants_phrases\":[{\"無線新聞台\":\"無綫新聞台\"},5,5]"
                + "}";

        DictionaryMaxlength dictionary = DictionaryMaxlength.fromJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals("喫茶小舖", dictionary.tw_variants_phrases.dict.get("喫茶小舖"));
        assertEquals("無綫新聞台", dictionary.hk_variants_phrases.dict.get("無線新聞台"));
    }

    @Test
    void fromDictsLoadsForwardRegionalVariantPhraseSlots() {
        DictionaryMaxlength dictionary = DictionaryMaxlength.fromDicts("dicts");

        assertNotNull(dictionary.tw_variants_phrases);
        assertNotNull(dictionary.hk_variants_phrases);
        assertEquals("喫茶小舖", dictionary.tw_variants_phrases.dict.get("喫茶小舖"));
        assertEquals("無綫新聞台", dictionary.hk_variants_phrases.dict.get("無線新聞台"));
        assertEquals(DictSlot.TWVariantsPhrases, DictSlot.valueOf("TWVariantsPhrases"));
        assertEquals(DictSlot.HKVariantsPhrases, DictSlot.valueOf("HKVariantsPhrases"));
    }

    @Test
    void customAppendAndOverrideCanPatchForwardVariantPhraseSlots() {
        DictionaryMaxlength original = DictionaryMaxlength.fromDicts("dicts");

        Map<String, String> twPairs = new LinkedHashMap<>();
        twPairs.put("測試詞", "測試詞臺");
        DictionaryMaxlength appended = original.withCustomDicts(Collections.singletonList(
                CustomDictSpec.fromPairs(DictSlot.TWVariantsPhrases, twPairs, CustomDictMode.Append)
        ));

        assertNull(original.tw_variants_phrases.dict.get("測試詞"));
        assertEquals("測試詞臺", appended.tw_variants_phrases.dict.get("測試詞"));
        assertEquals("喫茶小舖", appended.tw_variants_phrases.dict.get("喫茶小舖"));

        Map<String, String> hkPairs = new LinkedHashMap<>();
        hkPairs.put("測試詞", "測試詞港");
        DictionaryMaxlength overridden = original.withCustomDicts(Collections.singletonList(
                CustomDictSpec.fromPairs(DictSlot.HKVariantsPhrases, hkPairs, CustomDictMode.Override)
        ));

        assertEquals(1, overridden.hk_variants_phrases.dict.size());
        assertEquals("測試詞港", overridden.hk_variants_phrases.dict.get("測試詞"));
    }

    @Test
    void serializeToJsonUsesStableFieldOrderAndSortedKeys() throws Exception {
        DictionaryMaxlength dictionary = new DictionaryMaxlength();
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("b", "2");
        pairs.put("a", "1");
        dictionary.st_characters = new DictionaryMaxlength.DictEntry(pairs, 1, 1);
        dictionary.tw_variants = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("內", "內"),
                1,
                1
        );
        dictionary.tw_variants_phrases = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("喫茶小舖", "喫茶小舖"),
                4,
                4
        );
        dictionary.tw_variants_rev = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("內", "內"),
                1,
                1
        );
        dictionary.hk_variants = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("裏", "裡"),
                1,
                1
        );
        dictionary.hk_variants_phrases = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("無線新聞台", "無綫新聞台"),
                5,
                5
        );
        dictionary.hk_variants_rev = new DictionaryMaxlength.DictEntry(
                Collections.singletonMap("裡", "裏"),
                1,
                1
        );

        String json = dictionary.serializeToJsonString(false, true);

        assertTrue(json.indexOf("\"tw_variants\"") < json.indexOf("\"tw_variants_phrases\""));
        assertTrue(json.indexOf("\"tw_variants_phrases\"") < json.indexOf("\"tw_variants_rev\""));
        assertTrue(json.indexOf("\"hk_variants\"") < json.indexOf("\"hk_variants_phrases\""));
        assertTrue(json.indexOf("\"hk_variants_phrases\"") < json.indexOf("\"hk_variants_rev\""));
        assertTrue(json.indexOf("\"a\"") < json.indexOf("\"b\""));
        assertFalse(json.contains("\"st_phrases\""));

        DictionaryMaxlength roundTripped = DictionaryMaxlength.fromJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("喫茶小舖", roundTripped.tw_variants_phrases.dict.get("喫茶小舖"));
        assertEquals("無綫新聞台", roundTripped.hk_variants_phrases.dict.get("無線新聞台"));
    }

    @Test
    void dictgenOutputIncludesForwardVariantPhraseSlotsAndRoundTrips() throws Exception {
        Path output = Files.createTempFile("openccjavafx-dictgen-", ".json");
        try {
            int exitCode = new CommandLine(new DictgenCommand()).execute(
                    "--compact",
                    "--output",
                    output.toString()
            );

            assertEquals(0, exitCode);
            String json = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"tw_variants_phrases\""));
            assertTrue(json.contains("\"hk_variants_phrases\""));
            assertTrue(json.indexOf("\"tw_variants\"") < json.indexOf("\"tw_variants_phrases\""));
            assertTrue(json.indexOf("\"hk_variants\"") < json.indexOf("\"hk_variants_phrases\""));

            DictionaryMaxlength generated = DictionaryMaxlength.fromJson(output.toFile());
            assertEquals("喫茶小舖", generated.tw_variants_phrases.dict.get("喫茶小舖"));
            assertEquals("無綫新聞台", generated.hk_variants_phrases.dict.get("無線新聞台"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void forwardRegionalVariantsApplyPhrasesBeforeCharacters() {
        DictionaryMaxlength dict =
                DictionaryMaxlength.fromDicts("dicts");

        OpenCC taiwan = new OpenCC(OpenccConfig.T2TW, dict);
        OpenCC hongKong = new OpenCC(OpenccConfig.T2HK, dict);

        assertEquals("喫茶小舖", taiwan.convert("喫茶小舖"));
        assertEquals("喫茶小舖", hongKong.convert("喫茶小舖"));
        assertEquals("無綫新聞台", hongKong.convert("無線新聞台"));
    }

    @Test
    void officeConvertFailsWhenOutputFileIsNull() {
        OpenCC converter = new OpenCC("s2t");

        OfficeHelper.FileResult result = OfficeHelper.convert(
                new File("unused-input.docx"),
                null,
                "docx",
                converter,
                false,
                false
        );

        assertFalse(result.success);
        assertTrue(result.message.contains("Output file must not be null"));
    }
}
