package openccjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class UpstreamCustomDictionarySsotTest {

    @Test
    void typedFactoryPreservesLeadingSpaceInFilename(@TempDir Path directory) throws Exception {
        Path path = Paths.get(" custom.txt");
        CustomDictSpec spec = CustomDictSpec.fromFile(
                DictSlot.STPhrases, path, CustomDictMode.Append);
        assertEquals(path, spec.paths.get(0));

        Path file = directory.resolve(path);
        Files.write(file, Collections.singletonList("custom\treplacement"), StandardCharsets.UTF_8);
        CustomDictSpec absoluteSpec = CustomDictSpec.fromFile(
                DictSlot.STPhrases, file, CustomDictMode.Append);
        DictionaryMaxlength dictionary = new DictionaryMaxlength()
                .withCustomDicts(Collections.singletonList(absoluteSpec));
        assertEquals("replacement", dictionary.st_phrases.dict.get("custom"));
    }

    @Test
    void typedFactoriesPreserveZipFilesystemPaths(@TempDir Path directory) throws Exception {
        Path archive = directory.resolve("custom.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("custom.txt"));
            out.write("custom\treplacement\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Collections.<String, String>emptyMap())) {
            Path path = zip.getPath("/custom.txt");
            CustomDictSpec single = CustomDictSpec.fromFile(
                    DictSlot.STPhrases, path, CustomDictMode.Append);
            CustomDictSpec multiple = CustomDictSpec.fromFiles(
                    DictSlot.STPhrases, Collections.singletonList(path), CustomDictMode.Override);
            for (CustomDictSpec spec : Arrays.asList(single, multiple)) {
                assertSame(zip, spec.paths.get(0).getFileSystem());
                DictionaryMaxlength dictionary = new DictionaryMaxlength()
                        .withCustomDicts(Collections.singletonList(spec));
                assertEquals("replacement", dictionary.st_phrases.dict.get("custom"));
            }
        }
    }

    private static final List<DictSlot> EXPECTED_ACTIVE_SLOTS = Arrays.asList(
            DictSlot.STCharacters,
            DictSlot.STPhrases,
            DictSlot.STPunctuations,
            DictSlot.TSCharacters,
            DictSlot.TSPhrases,
            DictSlot.TSPunctuations,
            DictSlot.TWPhrases,
            DictSlot.TWPhrasesRev,
            DictSlot.TWVariants,
            DictSlot.TWVariantsPhrases,
            DictSlot.TWVariantsRev,
            DictSlot.TWVariantsRevPhrases,
            DictSlot.HKPhrases,
            DictSlot.HKPhrasesRev,
            DictSlot.HKVariants,
            DictSlot.HKVariantsPhrases,
            DictSlot.HKVariantsRev,
            DictSlot.HKVariantsRevPhrases,
            DictSlot.JPSCharacters,
            DictSlot.JPSCharactersRev,
            DictSlot.JPSPhrases,
            DictSlot.SealCharacters,
            DictSlot.SealCharactersRev,
            DictSlot.SealVariants,
            DictSlot.SealVariantsRev
    );

    @Test
    void exposesEveryActiveSlotAndCanonicalName() {
        assertEquals(EXPECTED_ACTIVE_SLOTS, DictSlot.activeSlots());
        assertEquals(EXPECTED_ACTIVE_SLOTS.size(), DictSlot.supportedCanonicalNames().size());

        assertEquals(
                String.join(", ", DictSlot.supportedCanonicalNames()),
                DictSlot.supportedSlotDisplay()
        );

        for (int i = 0; i < EXPECTED_ACTIVE_SLOTS.size(); i++) {
            DictSlot slot = EXPECTED_ACTIVE_SLOTS.get(i);
            assertTrue(slot.isActive());
            assertEquals(slot.name(), slot.toCanonicalName());
            assertEquals(slot.toCanonicalName(), DictSlot.supportedCanonicalNames().get(i));
            assertTrue(DictSlot.supportedSlotDisplay().contains(slot.toCanonicalName()));
        }
    }

    @Test
    void canonicalParsingIsCaseInsensitive() {
        for (DictSlot slot : EXPECTED_ACTIVE_SLOTS) {
            assertEquals(slot, DictSlot.parse(slot.toCanonicalName().toLowerCase(java.util.Locale.ROOT)));
            assertEquals(slot, DictSlot.parse(slot.toCanonicalName().toUpperCase(java.util.Locale.ROOT)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void rejectsNullBlankNumericUnknownDeprecatedAndAliasSlots() {
        for (String value : Arrays.asList(
                "", "   ", "0", "16", "unknown", "JPVariants",
                "JPVariantsRev", "ST-Phrases", "ST_Phrases"
        )) {
            assertThrows(IllegalArgumentException.class, () -> DictSlot.parse(value), value);
            assertNull(DictSlot.tryParse(value), value);
        }

        assertThrows(IllegalArgumentException.class, () -> DictSlot.parse(null));
        assertNull(DictSlot.tryParse(null));
        assertFalse(DictSlot.JPVariants.isActive());
        assertFalse(DictSlot.JPVariantsRev.isActive());
        assertThrows(IllegalArgumentException.class, DictSlot.JPVariants::toCanonicalName);
        assertThrows(IllegalArgumentException.class, DictSlot.JPVariantsRev::toCanonicalName);
    }

    @Test
    void parsesAppendAndOverrideTokens() {
        CustomDictSpec append = CustomDictSpec.parse("stphrases:append:custom.txt");
        CustomDictSpec override = CustomDictSpec.parse(" HKPhrasesRev : OvErRiDe : hk.txt ");

        assertEquals(DictSlot.STPhrases, append.slot);
        assertEquals(CustomDictMode.Append, append.mode);
        assertEquals(Paths.get("custom.txt"), append.paths.get(0));
        assertEquals(DictSlot.HKPhrasesRev, override.slot);
        assertEquals(CustomDictMode.Override, override.mode);
        assertEquals(Paths.get("hk.txt"), override.paths.get(0));
    }

    @Test
    void preservesTheWindowsDriveLetterColonInThePathField() {
        CustomDictSpec windows = CustomDictSpec.parse(
                "stphrases:append:C:\\data\\custom.txt"
        );

        assertEquals(Paths.get("C:\\data\\custom.txt"), windows.paths.get(0));
    }

    @Test
    void preservesAdditionalColonsWhenTheHostPathSyntaxAllowsThem() {
        String pathText = "data:regional:custom.txt";
        try {
            Path expected = Paths.get(pathText);
            CustomDictSpec spec = CustomDictSpec.parse(
                    "stphrases:override:" + pathText
            );
            assertEquals(expected, spec.paths.get(0));
        } catch (InvalidPathException e) {
            assertEquals('\\', File.separatorChar);
        }
    }

    @Test
    void rejectsMalformedSpecificationsModesAndEmptyPaths() {
        for (String value : Arrays.asList(
                "", "   ", "stphrases", "stphrases:append",
                "stphrases:append:", "stphrases:append:   ",
                ":append:custom.txt", "stphrases:merge:custom.txt",
                "stphrases:0:custom.txt", "unknown:append:custom.txt",
                "1:append:custom.txt", "JPVariants:append:custom.txt"
        )) {
            assertThrows(IllegalArgumentException.class, () -> CustomDictSpec.parse(value), value);
        }

        assertThrows(IllegalArgumentException.class, () -> CustomDictSpec.parse(null));
    }

    @Test
    void parsingAndTypedConstructionDoNotCheckFileExistence() {
        String missing = "missing/" + System.nanoTime() + ".txt";

        CustomDictSpec parsed = CustomDictSpec.parse("tsphrases:append:" + missing);
        Path missingPath = Paths.get(missing);
        CustomDictSpec typed = CustomDictSpec.fromFile(
                DictSlot.TSPhrases,
                missingPath,
                CustomDictMode.Override
        );

        assertEquals(missingPath, parsed.paths.get(0));
        assertEquals(missingPath, typed.paths.get(0));
    }

    @SuppressWarnings("deprecation")
    @Test
    void typedFactoriesRejectDeprecatedSlots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CustomDictSpec.fromFile(
                        DictSlot.JPVariants,
                        Paths.get("custom.txt"),
                        CustomDictMode.Append
                )
        );
    }

    @SuppressWarnings("deprecation")
    @Test
    void dictionaryApplicationRejectsProgrammaticallyConstructedDeprecatedSpec() throws Exception {
        Constructor<CustomDictSpec> constructor = CustomDictSpec.class.getDeclaredConstructor(
                DictSlot.class,
                List.class,
                java.util.Map.class,
                CustomDictMode.class
        );
        constructor.setAccessible(true);
        CustomDictSpec spec = constructor.newInstance(
                DictSlot.JPVariants,
                Collections.<Path>emptyList(),
                Collections.singletonMap("廣", "広"),
                CustomDictMode.Override
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DictionaryMaxlength().withCustomDicts(Collections.singletonList(spec))
        );
    }
}
