package org.example.openccjavafx;
import openccjava.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;

/** Dictionary snapshots are immutable after publication; converters belong to each consumer. */
public final class DictionaryService {
    private final DictionaryMaxlength base = loadBase();
    private DictionaryMaxlength active = base;
    private boolean custom;
    public OpenCC converter() { return new OpenCC(active); }
    public DictionaryMaxlength prepare(List<CustomDictSpec> specs) {
        return specs.isEmpty() ? base : base.withCustomDicts(specs);
    }
    public void activate(DictionaryMaxlength dictionary, boolean custom) {
        active = dictionary;
        this.custom = custom;
    }
    public boolean isCustom() { return custom; }
    private static DictionaryMaxlength loadBase() {
        Path dictionary = appDirectory().resolve("dicts/dictionary_maxlength.json");
        if (Files.isRegularFile(dictionary)) {
            try { return DictionaryMaxlength.fromJsonNoDeps(dictionary); }
            catch (IOException e) { throw new IllegalStateException("Failed to load application dictionary: " + dictionary, e); }
        }
        return OpenCC.DictionaryHolder.get();
    }
    public static Path appDirectory() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) return launcherDirectory(Path.of(launcher));
        try {
            Path location = Path.of(DictionaryService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (location.toString().endsWith(".jar")) {
                Path directory = location.getParent();
                return directory.getFileName().toString().equals("lib") ? directory.getParent() : directory;
            }
        } catch (java.net.URISyntaxException | SecurityException ignored) { }
        // Development runs use the project directory.
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
    static Path launcherDirectory(Path launcher) {
        Path directory = launcher.toAbsolutePath().normalize().getParent();
        String name = directory.getFileName().toString();
        if (name.equals("MacOS")) return directory.getParent().resolve("app");
        if (name.equals("bin")) return directory.getParent();
        return directory;
    }
    public static Path resolve(String path) { return appDirectory().resolve(path.trim()).normalize(); }
    public static Path generate(Path baseDir, Path outputDir, List<CustomDictSpec> specs) throws IOException {
        DictionaryMaxlength dictionary = DictionaryMaxlength.fromDicts(baseDir.toString());
        if (!specs.isEmpty()) dictionary = dictionary.withCustomDicts(specs);
        Path output = outputDir.resolve("dictionary_maxlength.json");
        Path temporary = Files.createTempFile(outputDir, ".dictionary-", ".json");
        try {
            dictionary.serializeToJsonNoDeps(temporary);
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
            return output;
        } finally { Files.deleteIfExists(temporary); }
    }
}
