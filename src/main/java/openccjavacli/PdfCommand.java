package openccjavacli;

import openccjava.OpenCC;
import openccjava.OpenccConfig;
import openccjava.TextConverter;
import pdfboxhelper.PdfBoxHelper;
import pdfboxhelper.PdfReflowHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subcommand for converting PDF files (extract + optional reflow + OpenCC).
 *
 * <p>Typical usage:
 * <pre>
 *   openccjava-cli pdf \
 *     -i input.pdf \
 *     -o output.txt \
 *     -c s2t \
 *     --punct \
 *     -H \
 *     -r \
 *     --compact
 * </pre>
 *
 * <ul>
 *   <li>Only text-embedded PDF files are supported (no OCR).</li>
 *   <li>Output is always saved as UTF-8 plain text.</li>
 * </ul>
 */
@Command(
        name = "pdf",
        description = "\033[1;34mExtract PDF text, optionally reflow CJK paragraphs, then convert with OpenccJava\033[0m",
        mixinStandardHelpOptions = true
)
public class PdfCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-i", "--input"},
            paramLabel = "<file>",
            description = "Input PDF file",
            required = true
    )
    private File input;

    @Option(
            names = {"-o", "--output"},
            paramLabel = "<file>",
            description = "Output text file (UTF-8). If omitted, '<name>_converted.txt' is used next to input."
    )
    private File output;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "<conversion>",
            completionCandidates = CliUtils.ConfigCandidates.class,
            description = "Conversion configuration. Supported: ${COMPLETION-CANDIDATES}"
    )
    private String config;

    @Option(
            names = {"-p", "--punct"},
            description = "Enable punctuation conversion (default: false)"
    )
    private boolean punct;

    @Option(
            names = {"-H", "--header"},
            description = "Insert per-page header markers into extracted text"
    )
    private boolean addHeader;

    @Option(
            names = {"-r", "--reflow"},
            description = "Reflow CJK paragraphs after extraction (default: false)"
    )
    private boolean reflow;

    @Option(
            names = {"-C", "--compact"},
            description = "Compact / tighten paragraph gaps after reflow (default: false)"
    )
    private boolean compact;

    @Option(
            names = {"-e", "--extract"},
            description = "Extract text from PDF document only (default: false)"
    )
    private boolean extract;

    @Option(names = {"-n", "--norm-compat"}, description = "Normalize CJK Compatibility Ideographs before conversion.")
    private boolean normCompat;

    @Option(
            names = {"-E", "--norm-compat-extended"},
            description = "Normalize extended Unicode compatibility/allograph forms and CJK Compatibility Ideographs before conversion."
    )
    private boolean normCompatExtended;

    @Option(
            names = "--detofu",
            paramLabel = "<level>",
            description = "Apply tofu-safe fallback after conversion: all, ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h, ext-i"
    )
    private String detofu;

    @Option(
            names = "--detofu-file",
            paramLabel = "<file>",
            description = "Load additional DeToFu fallback mappings from a UTF-8 text file. Custom mappings override built-in mappings (requires --detofu)"
    )
    private File detofuFile;

    @Option(
            names = {"-D", "--custom-dict"},
            paramLabel = "<slot:mode:path>",
            split = ",",
            completionCandidates = CliUtils.SlotCandidates.class,
            description = "Apply custom dictionary file. Format: slot:append|override:path. Can be repeated or comma-separated. Supported slots: ${COMPLETION-CANDIDATES}"
    )
    private List<String> customDictSpecs;

    private static final Logger LOGGER = Logger.getLogger(PdfCommand.class.getName());

    @Override
    public void run() {
        if (!extract) {
            if (config == null || !OpenccConfig.isValidConfig(config)) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(),
                        "Missing or invalid config: " + config + System.lineSeparator()
                                + "Supported configs: " + String.join(", ", OpenCC.getSupportedConfigs())
                );
            }

            CliUtils.validateDeTofuOptions(detofu, detofuFile);
        }

        if (extract) {
            List<String> ignoredOptions = createIgnoredOptions();

            if (!ignoredOptions.isEmpty()) {
                System.err.println(
                        "ℹ️  Note: " + String.join(", ", ignoredOptions)
                                + " have no effect in extract-only mode."
                );
            }
        }

        validateInputPdf();

        try {
            if (output == null) {
                String inputName = removeExtension(input.getName());
                String defaultName;
                if (extract) {
                    defaultName = inputName + "_extracted.txt";
                } else {
                    defaultName = inputName + "_converted.txt";
                }
                output = new File(input.getParentFile(), defaultName);
                String outPath = output.toPath().toAbsolutePath().normalize().toString();
                System.err.println("ℹ️ Output file not specified. Using: " + outPath);
            }

            ConsoleProgressBar progressBar = new ConsoleProgressBar(20);
            System.err.println("📄 Extracting PDF text...");
            String raw = PdfBoxHelper.extractText(
                    input,
                    addHeader,
                    progressBar::update
            );

            if (raw == null) {
                raw = "";
            }

            String processed = raw;
            if (reflow) {
                System.err.println("🧹 Reflowing CJK paragraphs...");
                processed = PdfReflowHelper.reflowCjkParagraphs(raw, addHeader, compact);
            }

            if (extract) {
                System.err.println("🔁 Writing PDF extracted text...");
                Files.write(output.toPath(), processed.getBytes(StandardCharsets.UTF_8));
            } else {
//                OpenCC opencc = new OpenCC(config);
                OpenCC opencc =
                        CliUtils.createOpenCC(config, customDictSpecs);

                TextConverter textConverter =
                        CliUtils.createTextConverter(
                                opencc,
                                punct,
                                normCompat,
                                normCompatExtended,
                                detofu,
                                detofuFile
                        );

                System.err.println("🔁 Converting with OpenccJava...");

                String converted =
                        textConverter.convert(processed);

                Files.write(
                        output.toPath(),
                        converted.getBytes(StandardCharsets.UTF_8)
                );
            }

            System.err.println("✅ PDF " + (extract ? "extraction" : "conversion") + " succeeded.");
            System.err.println("📄 Input : " + input.toPath().toAbsolutePath().normalize());
            System.err.println("📁 Output: " + output.toPath().toAbsolutePath().normalize());
            System.err.println("⚙️  Config: " + (extract ? "Extract only" : config +
                    (punct ? " (punct: on)" : " (punct: off)")) +
                    (addHeader ? ", header" : "") +
                    (reflow ? ", reflow" : "") +
                    (compact ? ", compact" : ""));
        } catch (IllegalArgumentException e) {
            System.err.println("❌ " + e.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during PDF conversion", ex);
            System.err.println("❌ Exception occurred: " + ex.getMessage());
            System.exit(1);
        }
    }

    private List<String> createIgnoredOptions() {
        List<String> ignoredOptions = new ArrayList<>();

        if (punct) {
            ignoredOptions.add("--punct");
        }
        if (normCompat) {
            ignoredOptions.add("--norm-compat");
        }
        if (normCompatExtended) {
            ignoredOptions.add("--norm-compat-extended");
        }
        if (detofu != null && !detofu.trim().isEmpty()) {
            ignoredOptions.add("--detofu");
        }
        if (detofuFile != null) {
            ignoredOptions.add("--detofu-file");
        }
        return ignoredOptions;
    }

    private void validateInputPdf() {
        if (!input.exists()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Input file does not exist: " + input.getAbsolutePath()
            );
        }
        if (!input.isFile()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Input path is not a file: " + input.getAbsolutePath()
            );
        }

        String ext = getExtension(input.getName()).toLowerCase();
        if (!".pdf".equals(ext)) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Input file is not a PDF: " + input.getName()
            );
        }
    }

    private String removeExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx != -1) ? filename.substring(0, idx) : filename;
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx != -1) ? filename.substring(idx) : "";
    }
}
