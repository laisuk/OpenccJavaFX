package openccjavacli;

import openccjava.OpenCC;
import pdfboxhelper.PdfBoxHelper;
import pdfboxhelper.PdfReflowHelper;
import picocli.CommandLine.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            names = {"--compact"},
            description = "Compact / tighten paragraph gaps after reflow (default: false)"
    )
    private boolean compact;

    @Option(
            names = {"-e", "--extract"},
            description = "Extract text from PDF document only (default: false)"
    )
    private boolean extract;

    @Option(
            names = {"-D", "--custom-dict"},
            paramLabel = "<slot:mode:path>",
            split = ",",
            description = "Apply custom dictionary file. Format: slot:append|override:path. Can be repeated or comma-separated."
    )
    private List<String> customDictSpecs;

    private static final Logger LOGGER = Logger.getLogger(PdfCommand.class.getName());

    @Override
    public void run() {
        if (!extract) {
            if (config == null ||
                    !OpenCC.getSupportedConfigs().contains(config.toLowerCase())) {
                System.err.println("❌ Missing or invalid config: " + config);
                return;
            }
        }

        if (extract && punct) {
            System.err.println("ℹ️  Note: --punct has no effect in extract-only mode.");
        }

        try {
            validateInputPdf();

            if (output == null) {
                String inputName = removeExtension(input.getName());
                String defaultName;
                if (extract) {
                    defaultName = inputName + "_extracted.txt";
                } else {
                    defaultName = inputName + "_converted.txt";
                }
                output = new File(input.getParentFile(), defaultName);
                System.err.println("ℹ️ Output file not specified. Using: " + output.getAbsolutePath());
            }

            // --- NEW: progress bar setup ---
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

            // Optional reflow
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
                OpenCC opencc = CliUtils.createOpenCC(config, customDictSpecs);
                // OpenCC conversion
                System.err.println("🔁 Converting with OpenccJava...");
                String converted = opencc.convert(processed, punct);
                // Save UTF-8
                Files.write(output.toPath(), converted.getBytes(StandardCharsets.UTF_8));
            }

            System.err.println("✅ PDF " + (extract ? "extraction" : "conversion") + " succeeded.");
            System.err.println("📄 Input : " + input.getAbsolutePath());
            System.err.println("📁 Output: " + output.getAbsolutePath());
            System.err.println("⚙️  Config: " + (extract ? "Extract only" : config +
                                                                            (punct ? " (punct on)" : " (punct off)")) +
                    (addHeader ? ", header" : "") +
                    (reflow ? ", reflow" : "") +
                    (compact ? ", compact" : ""));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during PDF conversion", ex);
            System.err.println("❌ Exception occurred: " + ex.getMessage());
            System.exit(1);
        }
    }


    // ---- helpers ---------------------------------------------------------

    private void validateInputPdf() {
        if (!input.exists()) {
            System.err.println("❌ Input file does not exist: " + input.getAbsolutePath());
            System.exit(1);
        }
        if (!input.isFile()) {
            System.err.println("❌ Input path is not a file: " + input.getAbsolutePath());
            System.exit(1);
        }

        String ext = getExtension(input.getName()).toLowerCase();
        if (!".pdf".equals(ext)) {
            System.err.println("❌ Input file is not a PDF: " + input.getName());
            System.exit(1);
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
