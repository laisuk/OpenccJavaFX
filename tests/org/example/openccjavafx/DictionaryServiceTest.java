package org.example.openccjavafx;

import openccjava.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryServiceTest {
    @TempDir Path temporary;

    @Test void packagedPathsResolveToApplicationResourcesRatherThanWorkingDirectory() {
        assertEquals(temporary, DictionaryService.launcherDirectory(temporary.resolve("OpenccJavaFX.exe")));
        assertEquals(temporary, DictionaryService.launcherDirectory(temporary.resolve("bin/OpenccJavaFX")));
        assertEquals(temporary.resolve("Contents/app"), DictionaryService.launcherDirectory(temporary.resolve("Contents/MacOS/OpenccJavaFX")));
    }

    private CustomDictSpec patch(String source, String target) throws Exception {
        Path file = temporary.resolve(source + ".txt");
        Files.writeString(file, source + "\t" + target + "\n");
        return CustomDictSpec.fromFile(DictSlot.STCharacters, file, CustomDictMode.Append);
    }

    @Test void applyingAlwaysUsesBaseAndEmptySpecsResetWhileExistingConsumersKeepTheirSnapshot() throws Exception {
        DictionaryService service = new DictionaryService();
        OpenCC original = service.converter();
        service.activate(service.prepare(List.of(patch("汉", "甲"))), true);
        OpenCC first = service.converter();
        assertEquals("甲", first.convert("汉"));
        assertEquals("漢", original.convert("汉"));
        service.activate(service.prepare(List.of(patch("语", "乙"))), true);
        assertEquals("漢乙", service.converter().convert("汉语"));
        assertEquals("甲", first.convert("汉"));
        service.activate(service.prepare(List.of()), false);
        assertFalse(service.isCustom());
        assertEquals("漢語", service.converter().convert("汉语"));
    }

    @Test void converterConfigurationBelongsToEachConsumer() {
        DictionaryService service = new DictionaryService();
        OpenCC batch = service.converter();
        OpenCC editor = service.converter();
        editor.setConfig("t2s");
        assertEquals("漢", batch.convert("汉"));
        assertEquals("汉", editor.convert("漢"));
    }

    @Test void failedPreparationPreservesActiveDictionary() throws Exception {
        DictionaryService service = new DictionaryService();
        service.activate(service.prepare(List.of(patch("汉", "甲"))), true);
        CustomDictSpec missing = CustomDictSpec.fromFile(DictSlot.STCharacters, temporary.resolve("missing.txt"), CustomDictMode.Append);
        assertThrows(RuntimeException.class, () -> service.prepare(List.of(missing)));
        assertEquals("甲", service.converter().convert("汉"));
        assertTrue(service.isCustom());
    }

    @Test void generationUsesTextBaseAndCustomSpecsAndProducesLoadableJson() throws Exception {
        Path output = DictionaryService.generate(Path.of("dicts").toAbsolutePath(), temporary, List.of(patch("汉", "甲")));
        assertEquals(temporary.resolve("dictionary_maxlength.json"), output);
        assertEquals("甲", new OpenCC(DictionaryMaxlength.fromJsonNoDeps(output)).convert("汉"));
        DictionaryService.generate(Path.of("dicts").toAbsolutePath(), temporary, List.of());
        assertEquals("漢", new OpenCC(DictionaryMaxlength.fromJsonNoDeps(output)).convert("汉"));
        try (var files = Files.list(temporary)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".dictionary-")));
        }
    }
}
