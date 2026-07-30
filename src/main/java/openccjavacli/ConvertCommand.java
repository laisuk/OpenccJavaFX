package openccjavacli;

import openccjava.*;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.*;

@Command(
        name = "convert",
        description = "\033[1;34mConvert plain text using OpenccJava\033[0m",
        mixinStandardHelpOptions = true
)
public class ConvertCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input file")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output file")
    private File output;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "<conversion>",
            required = true,
            completionCandidates = CliUtils.ConfigCandidates.class,
            description = "Conversion configuration. Supported: ${COMPLETION-CANDIDATES}"
    )
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"-n", "--norm-compat"}, description = "Normalize CJK Compatibility Ideographs before conversion.")
    private boolean normCompat;

    @Option(
            names = "--detofu",
            paramLabel = "<level>",
            description = "Apply tofu-safe fallback after conversion: all, ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h, ext-i"
    )
    private String detofu;

    @Option(
            names = "--detofu-file",
            paramLabel = "<file>",
            description = "Load additional DeTofu fallback mappings from a UTF-8 text file. Custom mappings override built-in mappings (requires --detofu)"
    )
    private File detofuFile;

    @Option(
            names = {"-D", "--custom-dict"},
            paramLabel = "<slot:mode:path>",
            split = ",",
            description = "Apply custom dictionary file. Format: slot:append|override:path. Can be repeated or comma-separated."
    )
    private List<String> customDictSpecs;

    @Option(names = {"--in-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Input encoding")
    private String inEncoding;

    @Option(names = {"--out-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Output encoding")
    private String outEncoding;

    @Option(
            names = "--con-enc",
            paramLabel = "<encoding>",
            description = "Console encoding for interactive mode. Ignored if not attached to a terminal. Common <encoding>: UTF-8, GBK, Big5",
            defaultValue = "UTF-8"
    )
    private String consoleEncoding;

    private static final Logger LOGGER = Logger.getLogger(ConvertCommand.class.getName());
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";

    @Override
    public Integer call() {
        config = normalizeConfig(config);

        if (!OpenCC.isSupportedConfig(config)) {
            printInvalidConfigError(config);
            return ExitCode.USAGE;
        }

        if (detofuFile != null && (detofu == null || detofu.trim().isEmpty())) {
            System.err.println("❌ --detofu-file requires --detofu");
            return ExitCode.USAGE;
        }

        return handleTextConversion();
    }

    private int handleTextConversion() {
        try {
//            OpenCC opencc = new OpenCC(config);
            OpenCC opencc = CliUtils.createOpenCC(config, customDictSpecs);
            String inputText;

            if (input != null) {
                byte[] bytes = Files.readAllBytes(input.toPath());
                inputText = new String(bytes, Charset.forName(inEncoding));
            } else {
                Charset inputCharset = Charset.forName(normEnc(inEncoding));
                if (System.console() != null) {
                    inputCharset = Charset.forName(normEnc(consoleEncoding));
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        System.err.println("Notes: If your terminal shows garbage characters, try setting:");
                        System.err.println("       --con-enc=GBK (Simplified Chinese Windows)");
                        System.err.println("       --con-enc=BIG5 (Traditional Chinese Windows)");
                    }
                    System.err.println("Input (Charset: " + inputCharset.name() + ")");
                    System.err.println(BLUE + "Input text to convert, <Ctrl+D> (Unix) <Ctrl-Z> (Windows) to submit:" + RESET);
                }
                inputText = new String(inputStreamReadAllBytes(), inputCharset);
            }

            if (normCompat) {
                inputText = opencc.normalizeCompat(inputText);
            }

            String outputText = opencc.convert(inputText, punct);

            if (detofu != null && !detofu.trim().isEmpty()) {
                DeTofu.Level level = DeTofu.Level.parse(detofu);

                outputText = detofuFile != null
                        ? opencc.deTofuWithCustomFile(outputText, level, detofuFile.getPath())
                        : opencc.deTofu(outputText, level);
            }

            if (output != null) {
                Files.write(output.toPath(), outputText.getBytes(Charset.forName(outEncoding)));
            } else {
                Charset outputCharset = Charset.forName(normEnc(consoleEncoding));
                System.err.println("Output (Charset: " + outputCharset.name() + ")");
                System.out.write(outputText.getBytes(outputCharset));
            }

            String inFrom = (input != null) ? input.getPath() : "<stdin>";
            String outTo = (output != null) ? output.getPath() : "stdout";
            if (System.console() != null) {
                if (!outputText.endsWith("\n")) {
                    System.err.println();
                }
                System.err.println(BLUE + "Conversion completed (" + config + "): " + inFrom + " → " + outTo + RESET);
            }
            return ExitCode.OK;
        } catch (IllegalArgumentException e) {
            System.err.println("❌ " + e.getMessage());
            return ExitCode.SOFTWARE;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during text conversion", e);
            System.err.println("❌ Exception occurred: " + e.getMessage());
            return ExitCode.SOFTWARE;
        }
    }

    private void printInvalidConfigError(String configValue) {
        PrintWriter err = spec.commandLine().getErr();
        err.println("❌ Invalid config: " + configValue);
        err.println("Supported configs: " + String.join(", ", OpenCC.getSupportedConfigs()));
        spec.commandLine().usage(err);
    }

    private static String normalizeConfig(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normEnc(String name) {
        if (name == null) return null;
        String n = name.trim();
        switch (n.toUpperCase(java.util.Locale.ROOT)) {
            case "UTF8":
                return "UTF-8";
            case "CP936":
            case "GB2312":
                return "GBK";
            case "CP950":
                return "Big5";
            default:
                return n;
        }
    }

    private static byte[] inputStreamReadAllBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = System.in.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }

}
