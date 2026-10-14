package openccjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SealConversionTest {
    private static final String SIMPLIFIED = "你好，小篆国际编码18";
    private static final String TRADITIONAL = "你好，小篆國際編碼18";
    private static final String SEAL = "你𿒛，𽌠𽴖𾇓𿭖𿛛碼18";

    @ParameterizedTest
    @EnumSource(value = OpenccConfig.class, names = {"S2SEAL", "T2SEAL", "SEAL2S", "SEAL2T"})
    void plansUseExpectedRoundSlotsWithAndWithoutPunctuation(OpenccConfig config) {
        DictionaryMaxlength d = OpenCC.DictionaryHolder.get();
        ConversionPlanCache cache = new ConversionPlanCache(() -> d);
        for (boolean punctuation : new boolean[]{false, true}) {
            List<List<DictionaryMaxlength.DictEntry>> rounds = new java.util.ArrayList<>();
            if (config == OpenccConfig.S2SEAL) {
                rounds.add(punctuation ? Arrays.asList(d.st_phrases, d.st_characters, d.st_punctuations)
                        : Arrays.asList(d.st_phrases, d.st_characters));
            }
            boolean forward = config == OpenccConfig.S2SEAL || config == OpenccConfig.T2SEAL;
            rounds.add(Collections.singletonList(forward ? d.seal_variants : d.seal_characters));
            rounds.add(Collections.singletonList(forward ? d.seal_characters_rev : d.seal_variants_rev));
            if (config == OpenccConfig.SEAL2S) {
                rounds.add(punctuation ? Arrays.asList(d.ts_phrases, d.ts_characters, d.ts_punctuations)
                        : Arrays.asList(d.ts_phrases, d.ts_characters));
            } else if (punctuation && config != OpenccConfig.S2SEAL) {
                rounds.add(Collections.singletonList(d.st_punctuations));
            }
            int[] round = {0};
            cache.getPlan(config, punctuation).applySegmentReplace("probe", (input, partition) -> {
                List<DictionaryMaxlength.DictEntry> actual = new java.util.ArrayList<>(partition.phraseDicts);
                actual.addAll(partition.singleDicts);
                assertEquals(rounds.get(round[0]++), actual);
                return input;
            });
            assertEquals(rounds.size(), round[0]);
        }
    }

    @ParameterizedTest
    @EnumSource(value = OpenccConfig.class, names = {"S2SEAL", "T2SEAL", "SEAL2S", "SEAL2T"})
    void configsAndDirectApisMatchStableRelease(OpenccConfig config) {
        String name = config.toCanonicalName();
        assertEquals(config, OpenccConfig.tryParse(" " + name.toUpperCase(Locale.ROOT) + " "));
        assertTrue(OpenccConfig.supportedCanonicalNames().contains(name));
        String input = config == OpenccConfig.S2SEAL ? SIMPLIFIED
                : config == OpenccConfig.T2SEAL ? TRADITIONAL : SEAL;
        String expected = config == OpenccConfig.SEAL2S ? SIMPLIFIED
                : config == OpenccConfig.SEAL2T ? TRADITIONAL : SEAL;
        OpenCC cc = new OpenCC(config);
        assertEquals(expected, cc.convert(input));
        assertEquals(expected, new OpenCC(name).convert(input));
        assertEquals(expected, OpenCC.convert(input, config));
        assertEquals(expected, OpenCC.convert(input, name));
        assertEquals("🙂ABC " + expected + "\n", cc.convert("🙂ABC " + input + "\n"));
        for (boolean punctuation : new boolean[]{false, true}) {
            String quoted = (config == OpenccConfig.SEAL2S ? "「" : "“") + input
                    + (config == OpenccConfig.SEAL2S ? "」" : "”");
            String target = punctuation
                    ? (config == OpenccConfig.SEAL2S ? "“" : "「") + expected
                        + (config == OpenccConfig.SEAL2S ? "”" : "」")
                    : (config == OpenccConfig.SEAL2S ? "「" : "“") + expected
                        + (config == OpenccConfig.SEAL2S ? "」" : "”");
            assertEquals(target, cc.convert(quoted, punctuation));
            assertEquals(target, direct(cc, config, quoted, punctuation));
        }
    }

    @Test
    void roundOrderAndIntermediateOutputsMatchStableRelease() {
        DictionaryMaxlength d = OpenCC.DictionaryHolder.get();
        ConversionPlanCache cache = new ConversionPlanCache(() -> d);
        DictRefs plan = cache.getPlan(OpenccConfig.T2SEAL, true);
        List<DictionaryMaxlength.DictEntry> slots = Arrays.asList(
                d.seal_variants, d.seal_characters_rev, d.st_punctuations);
        String[] outputs = {"你好，𡭔篆“國際編碼18”", "你𿒛，𽌠𽴖“𾇓𿭖𿛛碼18”",
                "你𿒛，𽌠𽴖「𾇓𿭖𿛛碼18」"};
        int[] round = {0};
        OpenCC cc = new OpenCC(OpenccConfig.T2SEAL, d);
        assertEquals(outputs[2], plan.applySegmentReplace("你好，小篆“國際編碼18”", (input, partition) -> {
            int i = round[0]++;
            assertSame(slots.get(i), partition.singleDicts.get(0));
            String output = cc.segmentReplaceWithUnion(input, partition);
            assertEquals(outputs[i], output);
            return output;
        }));
        assertEquals(3, round[0]);
        assertSame(plan, cache.getPlan(OpenccConfig.T2SEAL, true));
        assertSame(plan.u1, cache.getPlan(OpenccConfig.T2SEAL, false).u1);
        assertSame(plan.u2, cache.getPlan(OpenccConfig.S2SEAL, false).u3);
        int sealCp = SEAL.codePointAt(1);
        DictRefs reverse = cache.getPlan(OpenccConfig.SEAL2T, false);
        assertTrue(reverse.u1.hasStarter(sealCp));
        assertEquals(1L << 2, reverse.u1.lenMask(sealCp));
        assertEquals(2, reverse.u1.maxLen(sealCp));
        cache.clear();
        assertNotSame(plan, cache.getPlan(OpenccConfig.T2SEAL, true));
        assertNotSame(plan.u1, cache.getPlan(OpenccConfig.T2SEAL, true).u1);
    }

    @Test
    void textAndAllJsonCopiesLoadEquivalentSealSlots() throws Exception {
        DictionaryMaxlength text = DictionaryMaxlength.fromDicts();
        assertEquals("𡭔", text.seal_variants.dict.get("小"));
        assertEquals("𽌠", text.seal_characters_rev.dict.get("𡭔"));
        assertEquals(2, text.seal_characters.minLength);
        assertEquals(2, text.seal_characters.maxLength);
        try (InputStream in = getClass().getResourceAsStream("/dicts/dictionary_maxlength.json")) {
            assertNotNull(in);
            assertSealSlotsEqual(text, DictionaryMaxlength.fromJsonNoDeps(in));
        }
        assertSealSlotsEqual(text, DictionaryMaxlength.fromJsonNoDeps(Paths.get("dicts/dictionary_maxlength.json")));
        assertSealSlotsEqual(text, DictionaryMaxlength.fromJsonNoDeps(Paths.get("src/main/jpackage/dicts/dictionary_maxlength.json")));
        for (boolean pretty : new boolean[]{false, true}) {
            assertSealSlotsEqual(text, DictionaryMaxlength.fromJsonStringNoDeps(text.serializeToJsonString(pretty, true)));
        }
    }

    @Test
    void olderJsonAndTextFoldersKeepExistingConversions(@TempDir Path folder) throws Exception {
        DictionaryMaxlength legacy = DictionaryMaxlength.fromDicts();
        legacy.seal_characters = null;
        legacy.seal_characters_rev = null;
        legacy.seal_variants = null;
        legacy.seal_variants_rev = null;
        DictionaryMaxlength loaded = DictionaryMaxlength.fromJsonStringNoDeps(legacy.serializeToJsonString(false, false));
        for (DictionaryMaxlength.DictEntry entry : sealSlots(loaded)) assertTrue(entry.dict.isEmpty());
        assertEquals("漢字", new OpenCC(OpenccConfig.S2T, loaded).convert("汉字"));
        assertEquals("漢字", new OpenCC(OpenccConfig.T2SEAL, loaded).convert("漢字"));
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("dicts"), "*.txt")) {
            for (Path file : files) {
                if (!file.getFileName().toString().startsWith("Seal")) Files.copy(file, folder.resolve(file.getFileName()));
            }
        }
        for (DictionaryMaxlength.DictEntry entry : sealSlots(DictionaryMaxlength.fromDicts(folder.toString()))) {
            assertTrue(entry.dict.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = DictSlot.class, names = {"SealCharacters", "SealCharactersRev", "SealVariants", "SealVariantsRev"})
    void customSlotsCloneAndIsolateCachedPlans(DictSlot slot) throws Exception {
        DictionaryMaxlength original = DictionaryMaxlength.fromDicts();
        boolean forward = slot == DictSlot.SealCharactersRev || slot == DictSlot.SealVariants;
        OpenccConfig config = forward ? OpenccConfig.T2SEAL : OpenccConfig.SEAL2T;
        OpenCC originalCc = new OpenCC(config, original);
        assertEquals("A", originalCc.convert("A")); // Prepare the original plan first.
        DictionaryMaxlength appended = original.withCustomDicts(Collections.singletonList(
                CustomDictSpec.fromPairs(slot, Collections.singletonMap("A", "B"), CustomDictMode.Append)));
        int index = Arrays.asList(DictSlot.SealCharacters, DictSlot.SealCharactersRev,
                DictSlot.SealVariants, DictSlot.SealVariantsRev).indexOf(slot);
        assertEquals(sealSlots(original).get(index).dict.size() + 1, sealSlots(appended).get(index).dict.size());
        DictionaryMaxlength overridden = appended.withCustomDicts(Collections.singletonList(
                CustomDictSpec.fromPairs(slot, Collections.singletonMap("A", "C"), CustomDictMode.Override)));
        assertEquals(1, sealSlots(overridden).get(index).dict.size());
        assertEquals("B", new OpenCC(config, appended).convert("A"));
        assertEquals("C", new OpenCC(config, overridden).convert("A"));
        assertEquals("A", originalCc.convert("A"));
        for (int i = 0; i < 4; i++) {
            assertNotSame(sealSlots(original).get(i), sealSlots(appended).get(i));
            assertNotSame(sealSlots(original).get(i).dict, sealSlots(appended).get(i).dict);
        }
        assertSealSlotsEqual(overridden, DictionaryMaxlength.fromJsonStringNoDeps(overridden.serializeToJsonString(false, true)));
    }

    private static List<DictionaryMaxlength.DictEntry> sealSlots(DictionaryMaxlength d) {
        return Arrays.asList(d.seal_characters, d.seal_characters_rev, d.seal_variants, d.seal_variants_rev);
    }

    private static void assertSealSlotsEqual(DictionaryMaxlength expected, DictionaryMaxlength actual) {
        for (int i = 0; i < 4; i++) {
            DictionaryMaxlength.DictEntry a = sealSlots(expected).get(i), b = sealSlots(actual).get(i);
            assertNotNull(b);
            assertFalse(b.dict.isEmpty());
            assertEquals(a.dict, b.dict);
            assertEquals(a.maxLength, b.maxLength);
            assertEquals(a.minLength, b.minLength);
        }
    }

    private static String direct(OpenCC cc, OpenccConfig config, String input, boolean punctuation) {
        switch (config) {
            case S2SEAL: return cc.s2seal(input, punctuation);
            case T2SEAL: return cc.t2seal(input, punctuation);
            case SEAL2S: return cc.seal2s(input, punctuation);
            case SEAL2T: return cc.seal2t(input, punctuation);
            default: throw new AssertionError(config);
        }
    }
}
