package openccjavacli;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import picocli.CommandLine.*;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subcommand for converting Office documents using OpenCC.
 */
@Command(name = "office", description = "\033[1;34mConvert Office documents using OpenccJava\033[0m", mixinStandardHelpOptions = true)
public class OfficeCommand implements Runnable {

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input Office file", required = true)
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output Office file")
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

    @Option(names = {"-f", "--format"}, paramLabel = "<format>", description = "Target Office format (e.g., docx, xlsx, pptx, odt, epub)")
    private String format;

    @Option(names = {"-k", "--keep-font"}, defaultValue = "false", negatable = true, description = "Preserve font-family info (default: false)")
    private boolean keepFont;

    @Option(
            names = {"-D", "--custom-dict"},
            paramLabel = "<slot:mode:path>",
            split = ",",
            description = "Apply custom dictionary file. Format: slot:append|override:path. Can be repeated or comma-separated."
    )
    private List<String> customDictSpecs;

    private static final Logger LOGGER = Logger.getLogger(OfficeCommand.class.getName());

    @Override
    public void run() {
        String inputName = removeExtension(input.getName());
        String ext = getExtension(input.getName());

        String officeFormat;

        if (format != null) {
            officeFormat = format.toLowerCase();

            if (!OfficeHelper.OFFICE_FORMATS.contains(officeFormat)) {
                System.err.println("❌ Unsupported Office format: " + format);
                System.exit(1);
                return;
            }
        } else {
            if (ext.isEmpty() || !OfficeHelper.OFFICE_FORMATS.contains(ext.substring(1).toLowerCase())) {
                System.err.println("❌ Cannot infer Office format from input file extension.");
                System.exit(1);
                return;
            }

            officeFormat = ext.substring(1).toLowerCase();
        }

        if (output == null) {
            String defaultName = inputName + "_converted." + officeFormat;
            output = new File(input.getParentFile(), defaultName);
            System.err.println("ℹ️ Output file not specified. Using: " + output);
        }

        if (getExtension(output.getName()).isEmpty()) {
            output = new File(output.getAbsolutePath() + "." + officeFormat);
            System.err.println("ℹ️ Auto-extension applied: " + output.getAbsolutePath());
        }

        try {
//            OpenCC opencc = new OpenCC(config);
            OpenCC opencc = CliUtils.createOpenCC(config, customDictSpecs);
            OfficeHelper.FileResult result = OfficeHelper.convert(input, output, officeFormat, opencc, punct, keepFont);

            if (result.success) {
                System.err.println(result.message + "\n" +
                        "📁 Output saved to: " + output.getAbsolutePath());
            } else {
                System.err.println("❌ Office document conversion failed: " + result.message);
                System.exit(1);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during Office document conversion", ex);
            System.err.println("❌ Exception occurred: " + ex.getMessage());
            System.exit(1);
        }
    }

    private String removeExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(0, idx) : filename;
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(idx) : "";
    }
}
