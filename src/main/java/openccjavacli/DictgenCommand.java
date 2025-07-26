package openccjavacli;

import openccjava.DictionaryMaxlength;
import picocli.CommandLine.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

@Command(name = "dictgen", description = "\033[1;34mGenerate dictionary for OpenccJava\033[0m", mixinStandardHelpOptions = true)
public class DictgenCommand implements Runnable {

    @Option(names = {"-f", "--format"}, description = "Dictionary format: [json]", defaultValue = "json")
    private String format;

    @Option(names = {"-o", "--output"}, paramLabel = "<filename>", description = "Output filename")
    private String output;

    private static final Logger LOGGER = Logger.getLogger(DictgenCommand.class.getName());
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";

    @Override
    public void run() {
        try {
            String defaultOutput = "json".equals(format) ? "dictionary_maxlength.json" : null;
            if (defaultOutput == null) {
                LOGGER.severe("Unsupported format: " + format);
                System.exit(1);
            }

            String outputFile = (output != null) ? output : defaultOutput;
            File outputPath = Paths.get(outputFile).toAbsolutePath().toFile();

            DictionaryMaxlength dicts = DictionaryMaxlength.fromDicts();

            if ("json".equals(format)) {
                dicts.serializeToJson(outputPath.getAbsolutePath());
                System.out.println(BLUE + "Dictionary saved in JSON format at: " + outputPath + RESET);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during dictionary generation", e);
            System.exit(1);
        }
    }
}
