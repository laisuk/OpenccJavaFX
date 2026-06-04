package openccjavacli;

import openccjava.DictionaryMaxlength;
import picocli.CommandLine.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Command(name = "dictgen",
        description = "\033[1;34mGenerate base dictionary for OpenccJava\033[0m",
        mixinStandardHelpOptions = true)
public class DictgenCommand implements Runnable {

    @Option(names = {"-f", "--format"}, description = "Dictionary output format: [json]", defaultValue = "json")
    private String format;

    @Option(names = {"-o", "--output"}, paramLabel = "<filename>", description = "Output filename")
    private String output;

    @Option(names = {"-c", "--compact"}, description = "Enable non-indented JSON output (default: false)")
    private boolean compact;

    @Option(names = {"-s", "--sort"}, description = "Sort JSON dictionary keys for deterministic output")
    private boolean sort;

    private static final Logger LOGGER = Logger.getLogger(DictgenCommand.class.getName());
    private static final String GREEN = "\033[1;32m";
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";

    @Override
    public void run() {
        try {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            Path localDicts = cwd.resolve("dicts").normalize();

            boolean hasParentDicts = false;
            Path parentDicts = cwd.resolveSibling("dicts").normalize();
            if (parentDicts.getParent() != null &&
                    parentDicts.getParent().equals(cwd.getParent()) &&
                    Files.isDirectory(parentDicts)) {
                hasParentDicts = true;
                System.out.println(GREEN + "Found 'dicts/' in parent: " + parentDicts + RESET);
            }

            if (Files.isDirectory(localDicts)) {
                System.out.println(GREEN + "Using local 'dicts/' at: " + localDicts + RESET);
            } else if (!hasParentDicts) {
                System.out.print(BLUE +
                        "Local 'dicts/' not found (also not in '../dicts'). " +
                        "Download from GitHub? (y/N): " + RESET);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    String input = reader.readLine();
                    input = (input == null ? "" : input.trim().toLowerCase());
                    if (input.equals("y") || input.equals("yes")) {
                        Files.createDirectories(localDicts);
                        downloadDictsFromGithub(localDicts);
                        System.out.println(GREEN + "Dictionaries downloaded to: " + localDicts + RESET);
                    } else {
                        System.out.println("Using built-in dictionaries (fallback).");
                    }
                }
            }

            String defaultOutput = "json".equals(format) ? "dictionary_maxlength.json" : null;
            if (defaultOutput == null) {
                LOGGER.severe("Unsupported format: " + format);
                System.exit(1);
            }

            String outputFile = (output != null) ? output : defaultOutput;
            Path outputPath = Paths.get(outputFile).toAbsolutePath();

            DictionaryMaxlength dicts = DictionaryMaxlength.fromDicts();

            if ("json".equals(format)) {
                boolean sortKeys = sort || !compact;
                dicts.serializeToJson(outputPath, !compact, sortKeys);
                System.out.println(BLUE + "Dictionary saved in JSON format at: " + outputPath + RESET);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during dictionary generation", e);
            System.exit(1);
        }
    }

    private void downloadDictsFromGithub(Path dictDir) throws IOException {
        String[] dictFiles = {
                "STCharacters.txt", "STPhrases.txt", "STPunctuations.txt",
                "TSCharacters.txt", "TSPhrases.txt", "TSPunctuations.txt",
                "TWPhrases.txt", "TWPhrasesRev.txt",
                "TWVariants.txt", "TWVariantsPhrases.txt", "TWVariantsRev.txt", "TWVariantsRevPhrases.txt",
                "HKVariants.txt", "HKVariantsPhrases.txt", "HKVariantsRev.txt", "HKVariantsRevPhrases.txt",
                "JPShinjitaiCharacters.txt", "JPShinjitaiPhrases.txt", "JPVariants.txt", "JPVariantsRev.txt"
        };

        String baseUrl = "https://raw.githubusercontent.com/laisuk/OpenccJava/master/dicts/";

        Files.createDirectories(dictDir);

        for (String fileName : dictFiles) {
            String fileUrl = baseUrl + fileName;
            Path outputPath = dictDir.resolve(fileName);

            System.out.print(BLUE + "Downloading: " + fileName + "... " + RESET);

            try {
                URI uri = new URI(fileUrl);
                URL url = uri.toURL();

                try (InputStream in = url.openStream()) {
                    Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("done");
                }
            } catch (URISyntaxException e) {
                System.out.println("Failed to create URI for " + fileName);
                LOGGER.warning("Failed to create URI for " + fileUrl + ": " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Failed to download " + fileName);
                LOGGER.warning("Failed to download " + fileUrl + ": " + e.getMessage());
            }
        }
    }
}
