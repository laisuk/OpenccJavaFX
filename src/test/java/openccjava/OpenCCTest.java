package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpenCCTest {
    static OpenCC opencc;

    @BeforeAll
    static void init() {
        opencc = new OpenCC("s2t");
    }

    @Test
    void testConvertS2T() {
        String simplified = "简体中文测试";
        String expectedTraditional = "簡體中文測試"; // Ensure your dictionary has these mappings
        String result = opencc.convert(simplified);

        assertEquals(expectedTraditional, result);
        assertTrue(result.contains("簡"), "Should contain converted character");
    }

    @Test
    void testPunctuationConversionS2T() {
        String input = "“你好”";
        String result = opencc.s2t(input, true);
        assertEquals("「你好」", result);
    }

    @Test
    void testS2TwpCombinesTaiwanPhraseAndVariantNormalizationAfterS2T() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.S2TWP);

        assertEquals("軟體為", cc.convert("软件为"));
        assertEquals("軟體眾", cc.convert("软件众"));
    }

    @Test
    void testZhoCheckTraditional() {
        String text = "繁體中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(1, result); // 1 = traditional
    }

    @Test
    void testZhoCheckSimplified() {
        String text = "简体中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(2, result); // 2 = simplified
    }

    @Test
    void testZhoCheckUnknown() {
        String text = "hello world!";
        int result = OpenCC.zhoCheck(text);
        assertEquals(0, result); // not Chinese
    }

    @Test
    void testConfigFallback() {
        OpenCC bad = OpenCC.fromConfig("invalid_config");
        assertEquals("s2t", bad.getConfig());
        assertTrue(bad.hasLastError());
        assertNotNull(bad.getLastError());
        bad.clearLastError();
        assertNull(bad.getLastError());
    }

    @Test
    void staticConvertTypedNullConfigsUseDefault() {
        String stringConfig = null;
        OpenccConfig typedConfig = null;
        String input = "汉字";
        String expected = OpenCC.convert(input, OpenccConfig.defaultConfig());

        assertEquals(expected, OpenCC.convert(input, stringConfig));
        assertEquals(expected, OpenCC.convert(input, typedConfig));
    }

    @Test
    void testConfigEnum() {
        OpenccConfig configEnum = OpenccConfig.tryParse("s2twp");
        String ConfigStr = configEnum.toCanonicalName();
        assertTrue(OpenCC.isSupportedConfig(ConfigStr));
        assertEquals("s2twp", ConfigStr);
    }

    @Test
    void testConfigEnumRoundTrip() {
        // ✅ Case-insensitive matching
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("s2twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2Twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2TWP"));
        assertEquals(OpenccConfig.S2HKP, OpenccConfig.tryParse("s2hkp"));
        assertEquals(OpenccConfig.HK2SP, OpenccConfig.tryParse("hk2sp"));
        assertEquals(OpenccConfig.T2HKP, OpenccConfig.tryParse("t2hkp"));
        assertEquals(OpenccConfig.HK2TP, OpenccConfig.tryParse("hk2tp"));
        // ✅ Round-trip consistency
        for (OpenccConfig cfg : OpenccConfig.values()) {
            assertEquals(cfg, OpenccConfig.tryParse(cfg.toCanonicalName()));
            assertEquals(cfg.toCanonicalName(), cfg.toCanonicalName().toLowerCase()); // ensure lowercase form
        }
    }

    @Test
    void testSupportedConfigsIncludeDirectHongKongPhraseConfigs() {
        assertTrue(OpenCC.getSupportedConfigs().contains("s2hkp"));
        assertTrue(OpenCC.getSupportedConfigs().contains("hk2sp"));
        assertTrue(OpenCC.getSupportedConfigs().contains("t2hkp"));
        assertTrue(OpenCC.getSupportedConfigs().contains("hk2tp"));
    }

    @Test
    void testInvalidConfigTryParseReturnsNull() {
        // ✅ Null input → tolerant: returns null
        assertNull(OpenccConfig.tryParse(null));

        // ✅ Empty / whitespace → tolerant: returns null
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("   "));

        // ✅ Unknown config → tolerant: returns null
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testInvalidConfigIsRejected() {
        // ✅ Null / empty / whitespace
        assertFalse(OpenccConfig.isValidConfig(null));
        assertFalse(OpenccConfig.isValidConfig(""));
        assertFalse(OpenccConfig.isValidConfig("   "));

        // ✅ Unknown config
        assertFalse(OpenccConfig.isValidConfig("invalid"));
        assertFalse(OpenccConfig.isValidConfig("t2xyz"));

        // ✅ tryParse returns null for invalid inputs
        assertNull(OpenccConfig.tryParse(null));
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testTryParseRoundTrip() {
        for (OpenccConfig c : OpenccConfig.values()) {
            assertEquals(c, OpenccConfig.tryParse(c.toCanonicalName()));
            assertEquals(c, OpenccConfig.tryParse(c.name())); // enum-style
        }
    }

    @Test
    void testDirectHongKongPhraseConfigConversions() {
        DictionaryMaxlength dict = minimalDirectHongKongPhraseDictionary();

        OpenCC s2hkp = new OpenCC(OpenccConfig.S2HKP, dict);
        assertEquals("港字詞", s2hkp.convert("汉字词"));

        OpenCC hk2sp = new OpenCC(OpenccConfig.HK2SP, dict);
        assertEquals("汉字词", hk2sp.convert("港字詞"));

        OpenCC t2hkp = new OpenCC(OpenccConfig.T2HKP, dict);
        assertEquals("港字詞", t2hkp.convert("漢字詞"));
        OpenCC hk2tp = new OpenCC(OpenccConfig.HK2TP, dict);
        assertEquals("漢字詞", hk2tp.convert("港字詞"));
    }

    @Test
    void testDirectHongKongPhraseApis() {
        OpenCC cc = new OpenCC(OpenccConfig.S2T, minimalDirectHongKongPhraseDictionary());

        assertEquals("港字詞", cc.s2hkp("汉字词", false));
        assertEquals("汉字词", cc.hk2sp("港字詞", false));

        assertEquals("港字詞", cc.t2hkp("漢字詞", false));
        assertEquals("漢字詞", cc.hk2tp("港字詞", false));
    }

    @Test
    void testT2JPUsesShinjitaiCharactersRev() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.T2JP);

        assertEquals("旧字体：広国，読売。", cc.t2jp("舊字體：廣國，讀賣。", false));
    }

    @Test
    void testJP2TUsesShinjitaiPhrasesAndCharacters() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.JP2T);

        assertEquals("舊字體：廣國，讀賣。", cc.jp2t("旧字体：広国，読売。", false));
    }

    @ParameterizedTest
    @EnumSource(OpenccConfig.class)
    void testConfiguredPunctuationFlagForAllConfigs(OpenccConfig config) {
        OpenCC cc = OpenCC.fromConfig(config);
        boolean toSimplified = Arrays.asList(OpenccConfig.T2S, OpenccConfig.TW2S,
                OpenccConfig.TW2SP, OpenccConfig.HK2S, OpenccConfig.HK2SP).contains(config);
        String input = toSimplified ? "「你好」『世界』" : "“你好”‘世界’";
        String expected = toSimplified ? "“你好”‘世界’" : "「你好」『世界』";

        assertEquals(input, cc.convert(input, false));
        assertEquals(expected, cc.convert(input, true));
        assertEquals(input, cc.convert(input));
        assertEquals(input, cc.convert(input, false)); // Reusing the enabled plan must not affect false.
    }

    @ParameterizedTest
    @EnumSource(value = OpenccConfig.class, names = {
            "T2TW", "T2TWP", "TW2T", "TW2TP", "T2HK", "T2HKP", "HK2T", "HK2TP", "T2JP", "JP2T"
    })
    void testDirectPlansApplyPunctuationInSeparateSecondRound(OpenccConfig config) {
        ConversionPlanCache cache = ConversionPlanCache.forDictionary(OpenCC.DictionaryHolder.get());
        DictRefs plain = cache.getPlan(config, false);
        DictRefs punct = cache.getPlan(config, true);
        String input = "“你好”‘世界’";

        assertEquals(input, plain.applySegmentReplace(input, opencc::segmentReplaceWithUnion));
        assertEquals("「你好」『世界』", punct.applySegmentReplace(input, opencc::segmentReplaceWithUnion));
        assertSame(plain.u1, punct.u1); // Punctuation must not be merged into the primary union.
        assertNull(plain.u2);
        assertNull(punct.u3);
        punct.applySegmentReplace(input, (text, partition) -> {
            if (partition.union == punct.u2) {
                assertTrue(partition.phraseDicts.isEmpty());
                assertEquals(Collections.singletonList(OpenCC.DictionaryHolder.get().st_punctuations),
                        partition.singleDicts);
            }
            return text;
        });
        assertNotNull(punct.u2);
        assertSame(cache.getPlan(OpenccConfig.T2TW, true).u2, punct.u2);
    }

    @ParameterizedTest
    @CsvSource({"T2JP, 舊字體：廣國，讀賣。, 旧字体：広国，読売。",
            "JP2T, 旧字体：広国，読売。, 舊字體：廣國，讀賣。"})
    void testJapaneseConfiguredPunctuationFlag(OpenccConfig config, String input, String expected) {
        OpenCC cc = OpenCC.fromConfig(config);

        assertEquals("“" + expected + "”", cc.convert("“" + input + "”", false));
        assertEquals("「" + expected + "」", cc.convert("“" + input + "”", true));
        assertEquals("「" + expected + "」", config == OpenccConfig.T2JP
                ? cc.t2jp("“" + input + "”", true) : cc.jp2t("“" + input + "”", true));
        assertEquals("“" + expected + "”", config == OpenccConfig.T2JP
                ? cc.t2jp("“" + input + "”", false) : cc.jp2t("“" + input + "”", false));
    }

    private static DictionaryMaxlength minimalDirectHongKongPhraseDictionary() {
        DictionaryMaxlength dict = new DictionaryMaxlength();
        Map<String, String> stCharacters = new HashMap<>();
        stCharacters.put("汉", "漢");
        stCharacters.put("词", "詞");
        dict.st_characters = entry(stCharacters);
        dict.st_phrases = new DictionaryMaxlength.DictEntry();
        dict.st_punctuations = new DictionaryMaxlength.DictEntry();
        Map<String, String> tsCharacters = new HashMap<>();
        tsCharacters.put("漢", "汉");
        tsCharacters.put("詞", "词");
        dict.ts_characters = entry(tsCharacters);
        dict.ts_phrases = new DictionaryMaxlength.DictEntry();
        dict.ts_punctuations = new DictionaryMaxlength.DictEntry();
        dict.tw_phrases = new DictionaryMaxlength.DictEntry();
        dict.tw_phrases_rev = new DictionaryMaxlength.DictEntry();
        dict.tw_variants = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_phrases = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_rev = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_rev_phrases = new DictionaryMaxlength.DictEntry();
        dict.hk_phrases = entry(pair("漢字詞", "港字詞"));
        dict.hk_phrases_rev = entry(pair("港字詞", "漢字詞"));
        dict.hk_variants = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_phrases = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_rev = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_rev_phrases = new DictionaryMaxlength.DictEntry();
        dict.jps_characters = new DictionaryMaxlength.DictEntry();
        dict.jps_characters_rev = new DictionaryMaxlength.DictEntry();
        dict.jps_phrases = new DictionaryMaxlength.DictEntry();
        return dict;
    }

    private static DictionaryMaxlength.DictEntry entry(Map<String, String> pairs) {
        int maxLength = 0;
        int minLength = Integer.MAX_VALUE;
        for (String key : pairs.keySet()) {
            maxLength = Math.max(maxLength, key.length());
            minLength = Math.min(minLength, key.length());
        }
        if (pairs.isEmpty()) {
            minLength = 0;
        }
        return new DictionaryMaxlength.DictEntry(pairs, maxLength, minLength);
    }

    private static Map<String, String> pair(String key, String value) {
        Map<String, String> pairs = new HashMap<>();
        pairs.put(key, value);
        return pairs;
    }

    @Test
    void testSetVerboseLogging() {
        // Ensure initial state known
        OpenCC.setVerboseLogging(false);
        assertEquals(Level.OFF, OpenCC.LOGGER.getLevel(), "Logger should be OFF when disabled");
        // Enable verbose logging
        OpenCC.setVerboseLogging(true);
        assertEquals(Level.INFO, OpenCC.LOGGER.getLevel(), "Logger should be INFO when enabled");
        // Disable again
        OpenCC.setVerboseLogging(false);
        assertEquals(Level.OFF, OpenCC.LOGGER.getLevel(), "Logger should be OFF when disabled again");
    }

    @Test
    public void testConvert_s2t_100kChars() {
        String base = "“数大”便是美，碧绿的山坡前几千只绵羊，挨成一片的雪绒，是美；";

        StringBuilder sb = new StringBuilder(100_000);
        while (sb.length() < 100_000) {
            sb.append(base);
        }

        String input = sb.substring(0, 100_000);

        OpenCC cc = new OpenCC(OpenccConfig.S2T);

        long start = System.nanoTime();
        String output = cc.convert(input);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(output);
        assertEquals(input.length(), output.length());

        System.out.println("Converted 100K chars in " + durationMs + " ms");
    }

    @Test
    public void testS2T_100kCharacters() {
        // Generate 100,000 characters from a repeated simplified phrase
        String base = "“数大”便是美，碧绿的山坡前几千只绵羊，挨成一片的雪绒，是美；";
        StringBuilder inputBuilder = new StringBuilder(100_000);
        while (inputBuilder.length() < 100_000) {
            inputBuilder.append(base);
        }
        String input = inputBuilder.toString();
        // Create OpenCC instance (your optimized one)
        OpenCC opencc = OpenCC.fromConfig(OpenccConfig.S2T); // assuming your constructor loads config
        // Time the conversion
        long start = System.nanoTime();
        String output = opencc.s2t(input, false); // simplified to traditional
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Assertions
        assertEquals(OpenccConfig.values().length, OpenCC.getSupportedConfigIds().size());
        assertEquals(OpenccConfig.S2T, opencc.getConfigId());
        assertNotNull(output);
        assertEquals(input.length(), output.length()); // rough check, assuming 1:1 mapping
        System.out.println("s2t() conversion of 100K chars completed in " + durationMs + " ms");
    }

    @Test
    void testOpenCCFromDictsAppendCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-append-",
                ".txt"
        );

        Files.write(
                custom,
                Collections.singletonList("软件\t軟體"),
                StandardCharsets.UTF_8
        );

        OpenCC cc = OpenCC.fromDicts(
                OpenccConfig.S2T,
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Append
                        )
                )
        );

        String result = cc.convert("软件");

        assertEquals("軟體", result);

        Files.deleteIfExists(custom);
    }

    @Test
    void testOpenCCFromDictsOverrideCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-override-",
                ".txt"
        );

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "帕兰蒂尼\t勃蘭蒂尼"
                    ),
                    StandardCharsets.UTF_8
            );

            OpenCC cc = OpenCC.fromDicts(
                    OpenccConfig.S2T,
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals("專用詞", cc.convert("测试词"));
            assertEquals("勃蘭蒂尼", cc.convert("帕兰蒂尼"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testOpenCCFromDictsBaseOverrideCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-override-",
                ".txt"
        );

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "帕兰蒂尼\t勃蘭蒂尼"
                    ),
                    StandardCharsets.UTF_8
            );

            OpenCC cc = OpenCC.fromDicts(
                    OpenccConfig.S2T,
                    "dicts",
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals("專用詞", cc.convert("测试词"));
            assertEquals("勃蘭蒂尼", cc.convert("帕兰蒂尼"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    // DeTofu Tests

    @Test
    void testDeTofuBuiltin() {
        OpenCC cc = new OpenCC();

        String output = cc.deTofu(
                "骖𬴂",
                DeTofu.Level.ExtB
        );

        assertEquals("骖騑", output);
    }

    @Test
    void testDeTofuPreservesUnmappedCharacter() {
        OpenCC cc = new OpenCC();

        String output = cc.deTofu(
                "𱁬",
                DeTofu.Level.ExtB
        );

        assertEquals("𱁬", output);
    }

    @Test
    void testOpenCCT2SDeTofu() {
        OpenCC cc = new OpenCC(OpenccConfig.T2S);

        String output = cc.deTofu(
                cc.convert("儼驂騑於上路，訪風景於崇阿"),
                DeTofu.Level.ExtB
        );

        assertEquals(
                "俨骖騑于上路，访风景于崇阿",
                output
        );
    }

    @Test
    void testDeTofuWithCustomFileOverridesBuiltin() throws IOException {
        Path path = Files.createTempFile(
                "openccjava-detofu-",
                ".txt"
        );

        try {
            Files.write(
                    path,
                    "𣭲\t氂\tB\n".getBytes(StandardCharsets.UTF_8)
            );

            OpenCC cc = new OpenCC();

            String output = cc.deTofuWithCustomFile(
                    "𣭲毛",
                    DeTofu.Level.ExtB,
                    path.toString()
            );

            assertEquals("氂毛", output);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void testOpenCCT2SDeTofuPreservesUnmappedCharacter() {
        OpenCC cc = new OpenCC(OpenccConfig.T2S);

        String output = cc.deTofu(
                cc.convert("儼驂騑於上路，訪風景於崇阿，𱁬"),
                DeTofu.Level.ExtB
        );

        assertEquals(
                "俨骖騑于上路，访风景于崇阿，𱁬",
                output
        );
    }

    @Test
    void testDeTofuWithCustomPairsOverridesBuiltin() {
        Map<String, String> pairs = new HashMap<>();

        pairs.put("𣭲", "氂");

        OpenCC cc = new OpenCC();

        String output = cc.deTofuWithCustomPairs(
                "𣭲毛",
                DeTofu.Level.ExtB,
                pairs
        );

        assertEquals("氂毛", output);
    }

    // Unicode compatibility normalization tests

    @Test
    void normalizeCompatUsesPublicApi() {
        OpenCC cc = new OpenCC();

        assertEquals("金庸", cc.normalizeCompat("金庸"));
    }

    @Test
    void normalizeCompatHandlesNonBmpCompatibilityIdeographs() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "鼖鼻𪘀",
                cc.normalizeCompat("鼖鼻𪘀")
        );
    }

    @Test
    void normalizeCompatPreservesUnmappedCharacters() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "abc金庸123",
                cc.normalizeCompat("abc金庸123")
        );
    }

    @Test
    void normalizeCompatGolden() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "天龍八部書裡的喬峰是契丹人",
                cc.normalizeCompat("天龍八部書裡的喬峰是契丹人")
        );
    }

    @Test
    void normalizeUnicodeCompatGolden() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "聽聽奇美玉石瓶器音",
                cc.normalizeUnicodeCompat("聼聼竒羙⽟䂖甁噐⾳")
        );
    }

    @Test
    void normalizeUnicodeCompatDoesNotApplyCjkCompatIdeographsTable() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "金鼖",
                cc.normalizeUnicodeCompat("金鼖")
        );
    }

    @Test
    void normalizeCompatDoesNotApplyExtendedUnicodeCompatTable() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "聼竒羙䂖甁噐",
                cc.normalizeCompat("聼竒羙䂖甁噐")
        );
    }

    @Test
    void normalizeCompatExtendedGolden() {
        OpenCC cc = new OpenCC();

        assertEquals(
                "聽聽奇美玉石瓶器音",
                cc.normalizeCompatExtended("聼聼竒羙⽟䂖甁噐⾳")
        );
    }

    @Test
    void normalizeCompatExtendedMatchesExplicitComposition() {
        OpenCC cc = new OpenCC();

        String input = "聼聼竒羙⽟䂖甁噐⾳";

        assertEquals(
                cc.normalizeCompat(cc.normalizeUnicodeCompat(input)),
                cc.normalizeCompatExtended(input)
        );
    }

    @Test
    void normalizeCompatExtendedThenConvertGolden() {
        OpenCC cc = new OpenCC(OpenccConfig.T2S);

        String normalized =
                cc.normalizeCompatExtended("天龍八部書裡的聼眾");

        assertEquals(
                "天龍八部書裡的聽眾",
                normalized
        );

        assertEquals(
                "天龙八部书里的听众",
                cc.convert(normalized)
        );
    }

    @Test
    public void regionalPhrasePlansUseSingleSharedTripleRound() {
        ConversionPlanCache cache = ConversionPlanCache.forDictionary(OpenCC.DictionaryHolder.get());

        DictRefs t2twp = cache.getPlan(OpenccConfig.T2TWP, false);
        DictRefs s2twp = cache.getPlan(OpenccConfig.S2TWP, false);
        assertNotNull(t2twp.u1);
        assertNull(t2twp.u2);
        assertNull(t2twp.u3);
        assertSame(t2twp.u1, s2twp.u2);

        DictRefs tw2tp = cache.getPlan(OpenccConfig.TW2TP, false);
        DictRefs tw2sp = cache.getPlan(OpenccConfig.TW2SP, false);
        assertNotNull(tw2tp.u1);
        assertNull(tw2tp.u2);
        assertNull(tw2tp.u3);
        assertSame(tw2tp.u1, tw2sp.u1);

        DictRefs t2hkp = cache.getPlan(OpenccConfig.T2HKP, false);
        DictRefs s2hkp = cache.getPlan(OpenccConfig.S2HKP, false);
        assertNotNull(t2hkp.u1);
        assertNull(t2hkp.u2);
        assertNull(t2hkp.u3);
        assertSame(t2hkp.u1, s2hkp.u2);

        DictRefs hk2tp = cache.getPlan(OpenccConfig.HK2TP, false);
        DictRefs hk2sp = cache.getPlan(OpenccConfig.HK2SP, false);
        assertNotNull(hk2tp.u1);
        assertNull(hk2tp.u2);
        assertNull(hk2tp.u3);
        assertSame(hk2tp.u1, hk2sp.u1);
    }
}
