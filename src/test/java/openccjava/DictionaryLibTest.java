package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

//import java.io.File;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryLibTest {
    static final String TEST_JSON_OUTPUT = "test_dictionary_maxlength.json";
    static DictionaryMaxlength original;

    @BeforeAll
    static void setup() {
        original = DictionaryMaxlength.fromDicts();
    }

    @Test
    void testFromDictsLoadsAllFields() {
        assertNotNull(original);
        assertFalse(original.st_characters.dict.isEmpty(), "st_characters should be non-empty");
        assertFalse(original.ts_phrases.dict.isEmpty(), "ts_phrases should be non-empty");
        assertNotNull(original.tw_variants_phrases, "tw_variants_phrases should load strictly");
        assertNotNull(original.hk_phrases, "hk_phrases should load when present");
        assertNotNull(original.hk_phrases_rev, "hk_phrases_rev should load when present");
        assertNotNull(original.hk_variants_phrases, "hk_variants_phrases should load strictly");
        assertTrue(original.tw_variants_rev.maxLength > 0, "tw_variants_rev should have maxLength > 0");

    }

    @Test
    void testNewVariantPhraseSlotsExist() {
        assertEquals(DictSlot.TWVariantsPhrases, DictSlot.valueOf("TWVariantsPhrases"));
        assertEquals(DictSlot.HKPhrases, DictSlot.valueOf("HKPhrases"));
        assertEquals(DictSlot.HKPhrasesRev, DictSlot.valueOf("HKPhrasesRev"));
        assertEquals(DictSlot.HKVariantsPhrases, DictSlot.valueOf("HKVariantsPhrases"));
        assertEquals(DictSlot.JPSCharactersRev, DictSlot.valueOf("JPSCharactersRev"));
    }

    @Test
    void testMissingDirectHongKongPhraseTextDictsFallBackToEmpty(@TempDir Path tmp) throws IOException {
        Path source = Paths.get("dicts");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source, "*.txt")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if ("HKPhrases.txt".equals(name) || "HKPhrasesRev.txt".equals(name)) {
                    continue;
                }
                Files.copy(file, tmp.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        DictionaryMaxlength loaded = DictionaryMaxlength.fromDicts(tmp.toString());

        assertNotNull(loaded.hk_phrases);
        assertTrue(loaded.hk_phrases.dict.isEmpty());
        assertNotNull(loaded.hk_phrases_rev);
        assertTrue(loaded.hk_phrases_rev.dict.isEmpty());
    }

    @Test
    void testSerializeAndDeserializeJson() throws IOException {
        // Save to test file
        original.serializeToJsonNoDeps(TEST_JSON_OUTPUT);
        // Load again
        DictionaryMaxlength loaded = DictionaryMaxlength.fromJsonFileNoDeps(TEST_JSON_OUTPUT);
        // Validate one field at least
        assertEquals(
                original.st_characters.dict.get("你"),
                loaded.st_characters.dict.get("你"),
                "Key '你' translation must match"
        );

        assertEquals(
                original.st_characters.maxLength,
                loaded.st_characters.maxLength,
                "Max length should be preserved"
        );
        assertNotNull(loaded.tw_variants_phrases);
        assertNotNull(loaded.hk_variants_phrases);

        File file = new File(TEST_JSON_OUTPUT);
        file.deleteOnExit();
    }

    @Test
    void testToStringDisplaysCount() {
        String summary = original.toString();
        assertTrue(summary.contains("loaded dicts"), "toString should summarize dict count");
    }

    @Test
    void cleanupTestFile() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_JSON_OUTPUT));
    }

    // Use this to generate JSON file when modify DictionaryMaxlength structure
    @Test
    @Disabled
    void testSerializeToJsonFromDicts() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();
        // Where to output
        String outputPath = "dictionary_maxlength.json";
        // Serialize
        assertDoesNotThrow(() -> dict.serializeToJsonNoDeps(outputPath));
        // Confirm file is written
        File file = new File(outputPath);
        assertTrue(file.exists(), "Serialized JSON file should exist");
        assertTrue(file.length() > 0, "Serialized file should not be empty");

        System.out.println("✅ dictionary_maxlength.json written at: " + file.getAbsolutePath());
    }

    @Test
    void testFromDictsWithCustomAppendFile() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-", ".txt");
        Files.write(
                custom,
                Collections.singletonList("软件\t軟體"),
                StandardCharsets.UTF_8
        );

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Append
                        )
                )
        );

        assertEquals("軟體", dict.st_phrases.dict.get("软件"));
        assertTrue(dict.st_phrases.maxLength >= "软件".length());
        assertTrue(dict.st_phrases.minLength <= "软件".length());

        Files.deleteIfExists(custom);
    }

    @Test
    void testFromDictsWithCustomOverrideFile() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-override-", ".txt");
        Files.write(
                custom,
                Collections.singletonList("帕兰蒂尔\t柏蘭蒂爾"),
                StandardCharsets.UTF_8
        );

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Override
                        )
                )
        );

        assertEquals("柏蘭蒂爾", dict.st_phrases.dict.get("帕兰蒂尔"));
        assertEquals(1, dict.st_phrases.dict.size());
        assertEquals("帕兰蒂尔".length(), dict.st_phrases.maxLength);
        assertEquals("帕兰蒂尔".length(), dict.st_phrases.minLength);

        Files.deleteIfExists(custom);
    }

    @Test
    void testFromDictsWithCustomOverrideFileTwoEntries() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-override-", ".txt");

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "测试鼠标\t測試滑鼠"
                    ),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals(2, dict.st_phrases.dict.size());
            assertEquals("專用詞", dict.st_phrases.dict.get("测试词"));
            assertEquals("測試滑鼠", dict.st_phrases.dict.get("测试鼠标"));

            assertEquals("测试鼠标".length(), dict.st_phrases.maxLength);
            assertEquals("测试词".length(), dict.st_phrases.minLength);
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testWithCustomDictsAppendReturnsCustomizedCopy() throws IOException {
        Path custom = Files.createTempFile("openccjava-postload-append-", ".txt");

        try {
            Files.write(
                    custom,
                    Collections.singletonList("测试词\t專用詞"),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

            DictionaryMaxlength customized = base.withCustomDicts(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Append
                            )
                    )
            );

            assertNotSame(base, customized);
            assertNull(base.st_phrases.dict.get("测试词"));
            assertEquals("專用詞", customized.st_phrases.dict.get("测试词"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testWithCustomDictsOverrideReturnsCustomizedCopy() throws IOException {
        Path custom = Files.createTempFile("openccjava-postload-override-", ".txt");

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "测试鼠标\t測試滑鼠"
                    ),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

            DictionaryMaxlength customized = base.withCustomDicts(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertNotSame(base, customized);

            assertTrue(base.st_phrases.dict.size() > 2);
            assertEquals(2, customized.st_phrases.dict.size());

            assertEquals("專用詞", customized.st_phrases.dict.get("测试词"));
            assertEquals("測試滑鼠", customized.st_phrases.dict.get("测试鼠标"));

            assertEquals("测试鼠标".length(), customized.st_phrases.maxLength);
            assertEquals("测试词".length(), customized.st_phrases.minLength);
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void testWithCustomDictFilesDelegatesToWithCustomDicts() {
        DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

        //noinspection deprecation
        DictionaryMaxlength customized =
                base.withCustomDictFiles(Collections.emptyList());

        assertNotSame(base, customized);
    }

    @Test
    void testWithCustomDictsOverrideReturnsCustomizedCopy2() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("测试词", "專用詞");
        pairs.put("测试鼠标", "測試滑鼠");

        DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

        DictionaryMaxlength customized = base.withCustomDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.STPhrases,
                                pairs,
                                CustomDictMode.Override
                        )
                )
        );

        assertNotSame(base, customized);

        assertTrue(base.st_phrases.dict.size() > 2);
        assertEquals(2, customized.st_phrases.dict.size());

        assertEquals("專用詞", customized.st_phrases.dict.get("测试词"));
        assertEquals("測試滑鼠", customized.st_phrases.dict.get("测试鼠标"));

        assertEquals("测试鼠标".length(), customized.st_phrases.maxLength);
        assertEquals("测试词".length(), customized.st_phrases.minLength);
    }

    @Test
    void testWithCustomDictsPreservesNewVariantPhraseSlots() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("喫茶小舖", "喫茶小舖");

        DictionaryMaxlength base = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.TWVariantsPhrases,
                                pairs,
                                CustomDictMode.Append
                        )
                )
        );
        DictionaryMaxlength customized = base.withCustomDicts(Collections.emptyList());

        assertNotSame(base, customized);
        assertEquals("喫茶小舖", customized.tw_variants_phrases.dict.get("喫茶小舖"));
        assertNotNull(customized.hk_variants_phrases);
    }

    @Test
    void testCustomAppendWorksForTWVariantsPhrases() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("喫茶小舖", "喫茶小舖");

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.TWVariantsPhrases,
                                pairs,
                                CustomDictMode.Append
                        )
                )
        );

        assertEquals("喫茶小舖", dict.tw_variants_phrases.dict.get("喫茶小舖"));
    }

    @Test
    void testCustomOverrideWorksForHKVariantsPhrases() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("喫茶小舖", "喫茶小舖");

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.HKVariantsPhrases,
                                pairs,
                                CustomDictMode.Override
                        )
                )
        );

        assertEquals(1, dict.hk_variants_phrases.dict.size());
        assertEquals("喫茶小舖", dict.hk_variants_phrases.dict.get("喫茶小舖"));
    }

    @Test
    void testTWVariantPhrasesApplyBeforeCharacters() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(Arrays.asList(
                CustomDictSpec.fromPairs(
                        DictSlot.TWVariantsPhrases,
                        Collections.singletonMap("喫茶小舖", "喫茶小舖"),
                        CustomDictMode.Override
                ),
                CustomDictSpec.fromPairs(
                        DictSlot.TWVariants,
                        Collections.singletonMap("舖", "鋪"),
                        CustomDictMode.Override
                )
        ));

        OpenCC opencc = new OpenCC(OpenccConfig.T2TW, dict);

        assertEquals("喫茶小舖", opencc.convert("喫茶小舖", false));
    }

    @Test
    void testHKVariantPhrasesApplyBeforeCharacters() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(Arrays.asList(
                CustomDictSpec.fromPairs(
                        DictSlot.HKVariantsPhrases,
                        Collections.singletonMap("喫茶小舖", "喫茶小舖"),
                        CustomDictMode.Override
                ),
                CustomDictSpec.fromPairs(
                        DictSlot.HKVariants,
                        Collections.singletonMap("舖", "鋪"),
                        CustomDictMode.Override
                )
        ));

        OpenCC opencc = new OpenCC(OpenccConfig.T2HK, dict);

        assertEquals("喫茶小舖", opencc.convert("喫茶小舖", false));
    }

    @Test
    void testCustomJPSCharactersRevOverrideAffectsT2JP() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.JPSCharactersRev,
                                Collections.singletonMap("廣", "广"),
                                CustomDictMode.Override
                        )
                )
        );

        OpenCC opencc = new OpenCC(OpenccConfig.T2JP, dict);

        assertEquals("广", opencc.convert("廣", false));
    }

    @Test
    void testCustomJPSCharactersOverrideAffectsJP2T() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromPairs(
                                DictSlot.JPSCharacters,
                                Collections.singletonMap("広", "廣廣"),
                                CustomDictMode.Override
                        )
                )
        );

        OpenCC opencc = new OpenCC(OpenccConfig.JP2T, dict);

        assertEquals("廣廣", opencc.convert("広", false));
    }

    @Test
    void testCustomJPSPhrasesOverrideHasPriorityInJP2T() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(Arrays.asList(
                CustomDictSpec.fromPairs(
                        DictSlot.JPSPhrases,
                        Collections.singletonMap("広国字", "PHRASE"),
                        CustomDictMode.Override
                ),
                CustomDictSpec.fromPairs(
                        DictSlot.JPSCharacters,
                        Collections.singletonMap("広", "CHAR"),
                        CustomDictMode.Override
                )
        ));

        OpenCC opencc = new OpenCC(OpenccConfig.JP2T, dict);

        assertEquals("PHRASE", opencc.convert("広国字", false));
    }

    @Test
    void testReverseVariantPairBehaviorRemainsPhraseBeforeCharacters() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(Arrays.asList(
                CustomDictSpec.fromPairs(
                        DictSlot.TWVariantsRevPhrases,
                        Collections.singletonMap("喫茶小舖", "喫茶小舖"),
                        CustomDictMode.Override
                ),
                CustomDictSpec.fromPairs(
                        DictSlot.TWVariantsRev,
                        Collections.singletonMap("舖", "鋪"),
                        CustomDictMode.Override
                )
        ));

        OpenCC opencc = new OpenCC(OpenccConfig.TW2T, dict);

        assertEquals("喫茶小舖", opencc.convert("喫茶小舖", false));
    }

    @SuppressWarnings("deprecation")
    @Test
    void testLegacyJPVariantSlotsRemainDefinedButAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CustomDictSpec.fromPairs(
                        DictSlot.JPVariants,
                        Collections.singletonMap("廣", "広"),
                        CustomDictMode.Override
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CustomDictSpec.fromPairs(
                        DictSlot.JPVariantsRev,
                        Collections.singletonMap("広", "廣"),
                        CustomDictMode.Override
                )
        );
    }
}
