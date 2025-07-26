package openccjavacli;

import openccjava.OpenCC;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.*;

/**
 * Subcommand for converting plain text using OpenCC.
 */
@Command(name = "convert", description = "\033[1;34mConvert plain text using OpenccJava\033[0m", mixinStandardHelpOptions = true)
public class ConvertCommand implements Runnable {
    @Option(names = "--list-configs", description = "List all supported OpenccJava conversion configurations")
    private boolean listConfigs;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input file")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output file")
    private File output;

    @Option(names = {"-c", "--config"}, paramLabel = "<conversion>", description = "Conversion configuration", required = true)
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"--in-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Input encoding")
    private String inEncoding;

    @Option(names = {"--out-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Output encoding")
    private String outEncoding;

    private static final Logger LOGGER = Logger.getLogger(ConvertCommand.class.getName());
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";

    @Override
    public void run() {
        if (listConfigs) {
            System.out.println("Available OpenccJava configurations:");
            OpenCC.getSupportedConfigs().forEach(cfg -> System.out.println("  " + cfg));
            return;
        }

        handleTextConversion();
    }

    private void handleTextConversion() {
        try {
            OpenCC opencc = new OpenCC(config);
            String inputText;

            if (input != null) {
                inputText = Files.readString(input.toPath(), Charset.forName(inEncoding));
            } else {
                Charset inputCharset = Charset.forName(inEncoding);
                if (System.console() != null) {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        System.err.println("Notes: If your terminal shows garbage characters, try adding:");
                        System.err.println("       --in-enc=GBK --out-enc=GBK   (Simplified Chinese Windows)");
                        System.err.println("       --in-enc=BIG5 --out-enc=BIG5 (Traditional Chinese Windows)");
                        inputCharset = Objects.equals(inEncoding, "UTF-8")
                                ? Charset.forName("GBK")
                                : inputCharset;
                    }
                    System.err.println("Input (Charset: " + inputCharset + ")");
                    System.err.println("Input text to convert, <Ctrl+D> (Unix) <Ctrl-Z> (Windows) to submit:");
                }
                inputText = new String(System.in.readAllBytes(), inputCharset);
            }

            String outputText = opencc.convert(inputText, punct);

            if (output != null) {
                Files.writeString(output.toPath(), outputText, Charset.forName(outEncoding));
            } else {
                Charset outputCharset = Charset.forName(outEncoding);
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    outputCharset = Objects.equals(outEncoding, "UTF-8")
                            ? Charset.forName("GBK")
                            : outputCharset;
                }
                System.err.println("Output (Charset: " + outputCharset + ")");
                System.out.write(outputText.getBytes(outputCharset));
            }

            String inFrom = (input != null) ? input.getPath() : "<stdin>";
            String outTo = (output != null) ? output.getPath() : "stdout";
            if (System.console() != null) {
                System.err.println(BLUE + "Conversion completed (" + config + "): " + inFrom + " → " + outTo + RESET);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during text conversion", e);
            System.err.println("❌ Exception occurred: " + e.getMessage());
            System.exit(1);
        }
    }
}
