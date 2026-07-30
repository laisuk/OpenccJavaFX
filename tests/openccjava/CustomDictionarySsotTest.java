package openccjava;

import openccjavacli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CustomDictionarySsotTest {
    private static final List<DictSlot> ACTIVE_SLOTS = Arrays.asList(
            DictSlot.STCharacters, DictSlot.STPhrases, DictSlot.STPunctuations,
            DictSlot.TSCharacters, DictSlot.TSPhrases, DictSlot.TSPunctuations,
            DictSlot.TWPhrases, DictSlot.TWPhrasesRev, DictSlot.TWVariants,
            DictSlot.TWVariantsPhrases, DictSlot.TWVariantsRev,
            DictSlot.TWVariantsRevPhrases, DictSlot.HKPhrases,
            DictSlot.HKPhrasesRev, DictSlot.HKVariants,
            DictSlot.HKVariantsPhrases, DictSlot.HKVariantsRev,
            DictSlot.HKVariantsRevPhrases, DictSlot.JPSCharacters,
            DictSlot.JPSCharactersRev, DictSlot.JPSPhrases
    );

    @Test
    void exposesAndParsesEveryActiveCanonicalSlot() {
        assertEquals(ACTIVE_SLOTS, DictSlot.activeSlots());
        assertEquals(ACTIVE_SLOTS.size(), DictSlot.supportedCanonicalNames().size());
        assertEquals(String.join(", ", DictSlot.supportedCanonicalNames()),
                DictSlot.supportedSlotDisplay());

        for (int i = 0; i < ACTIVE_SLOTS.size(); i++) {
            DictSlot slot = ACTIVE_SLOTS.get(i);
            String canonical = slot.toCanonicalName();
            assertTrue(slot.isActive());
            assertEquals(canonical, DictSlot.supportedCanonicalNames().get(i));
            assertEquals(slot, DictSlot.parse(canonical.toLowerCase(Locale.ROOT)));
            assertEquals(slot, DictSlot.parse(canonical.toUpperCase(Locale.ROOT)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void rejectsNullBlankNumericUnknownDeprecatedAndAliasSlots() {
        for (String token : Arrays.asList(
                "", "   ", "0", "16", "unknown", "JPVariants",
                "JPVariantsRev", "ST-Phrases", "ST_Phrases"
        )) {
            assertThrows(IllegalArgumentException.class, () -> DictSlot.parse(token), token);
            assertNull(DictSlot.tryParse(token), token);
        }

        assertThrows(IllegalArgumentException.class, () -> DictSlot.parse(null));
        assertNull(DictSlot.tryParse(null));
        assertFalse(DictSlot.JPVariants.isActive());
        assertFalse(DictSlot.JPVariantsRev.isActive());
        assertThrows(IllegalArgumentException.class, () -> CustomDictSpec.fromPairs(
                DictSlot.JPVariants,
                Collections.singletonMap("廣", "広"),
                CustomDictMode.Override
        ));
    }

    @Test
    void parsesModesAndPreservesPortablePathText() {
        CustomDictSpec append = CustomDictSpec.parse("stphrases:append:custom.txt");
        CustomDictSpec override = CustomDictSpec.parse(
                " HKPhrasesRev : OvErRiDe : C:\\data\\custom.txt "
        );

        assertEquals(DictSlot.STPhrases, append.slot);
        assertEquals(CustomDictMode.Append, append.mode);
        assertEquals(Paths.get("custom.txt"), append.paths.get(0));
        assertEquals(DictSlot.HKPhrasesRev, override.slot);
        assertEquals(CustomDictMode.Override, override.mode);
        assertEquals(Paths.get("C:\\data\\custom.txt"), override.paths.get(0));

        String extraColons = "data:regional:custom.txt";
        try {
            assertEquals(Paths.get(extraColons), CustomDictSpec.parse(
                    "stphrases:append:" + extraColons
            ).paths.get(0));
        } catch (InvalidPathException expectedOnWindows) {
            assertEquals('\\', File.separatorChar);
        }
    }

    @Test
    void rejectsMalformedSpecsAndDoesNotCheckFileExistence() {
        for (String token : Arrays.asList(
                "", "   ", "stphrases", "stphrases:append",
                "stphrases:append:", "stphrases:append:   ",
                ":append:custom.txt", "stphrases:merge:custom.txt",
                "stphrases:0:custom.txt", "unknown:append:custom.txt",
                "1:append:custom.txt", "JPVariants:append:custom.txt"
        )) {
            assertThrows(IllegalArgumentException.class,
                    () -> CustomDictSpec.parse(token), token);
        }
        assertThrows(IllegalArgumentException.class, () -> CustomDictSpec.parse(null));

        Path missing = Paths.get("missing", System.nanoTime() + ".txt");
        assertEquals(missing, CustomDictSpec.parse(
                "tsphrases:append:" + missing
        ).paths.get(0));
        assertEquals(missing, CustomDictSpec.fromFile(
                DictSlot.TSPhrases, missing, CustomDictMode.Override
        ).paths.get(0));
    }

    @Test
    void repeatedCliCustomDictionaryOptionsRemainOrdered(@TempDir Path tempDir) throws Exception {
        Path first = tempDir.resolve("first.txt");
        Path second = tempDir.resolve("second.txt");
        Path input = tempDir.resolve("input.txt");
        Path output = tempDir.resolve("output.txt");
        Files.write(first, "甲\t一\n".getBytes(StandardCharsets.UTF_8));
        Files.write(second, "乙\t二\n".getBytes(StandardCharsets.UTF_8));
        Files.write(input, "甲乙".getBytes(StandardCharsets.UTF_8));

        int exitCode = new CommandLine(new Main()).execute(
                "convert", "-c", "s2t",
                "-D", "STCharacters:override:" + first,
                "-D", "STCharacters:append:" + second,
                "-i", input.toString(), "-o", output.toString()
        );

        assertEquals(0, exitCode);
        assertEquals("一二", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    @Test
    void mirrorsAdditionalCoreSafetyAndConvenienceFixes() {
        assertEquals(DeTofu.Level.ExtB, DeTofu.Level.parse("extb"));
        assertEquals(DeTofu.Level.ExtI, DeTofu.Level.parse("EXT-I"));
        assertEquals("漢", OpenCC.convert("汉", OpenccConfig.S2T));
        assertEquals("漢", OpenCC.convert("汉", "s2t"));

        BitSet bmp = new BitSet();
        bmp.set('甲');
        long[] lengths = new long[Character.MAX_VALUE + 1];
        lengths['甲'] = 1L << 1;
        StarterUnion union = new StarterUnion(
                bmp, new BitSet(), lengths, Collections.emptyMap()
        );
        bmp.clear('甲');
        lengths['甲'] = 0L;

        assertTrue(union.hasStarter('甲'));
        assertEquals(1L << 1, union.lenMask('甲'));
        assertEquals(0L, union.lenMask(-1));
    }
}
