package openccjavacli;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import picocli.CommandLine.*;

import java.io.File;
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

    @Option(names = {"-c", "--config"}, paramLabel = "<conversion>", description = "Conversion configuration", required = true)
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"--format"}, paramLabel = "<format>", description = "Target Office format (e.g., docx, xlsx, pptx, odt, epub)")
    private String format;

    @Option(names = {"--auto-ext"}, description = "Auto-append extension to output file")
    private boolean autoExt;

    @Option(names = {"--keep-font"}, defaultValue = "false", negatable = true, description = "Preserve font-family info (default: false)")
    private boolean keepFont;

    private static final Logger LOGGER = Logger.getLogger(OfficeCommand.class.getName());

    @Override
    public void run() {
        String officeFormat = OfficeHelper.OFFICE_FORMATS.contains(format) ? format : null;
        String inputName = removeExtension(input.getName());
        String ext = getExtension(input.getName());

        if (output == null) {
            String defaultExt = autoExt && format != null ? "." + format : ext;
            String defaultName = inputName + "_converted" + defaultExt;
            output = new File(input.getParentFile(), defaultName);
            System.err.println("ℹ️ Output file not specified. Using: " + output);
        }

        if (officeFormat == null) {
            if (ext.isEmpty() || !OfficeHelper.OFFICE_FORMATS.contains(ext.substring(1).toLowerCase())) {
                System.err.println("❌ Cannot infer Office format from input file extension.");
                System.exit(1);
            }
            officeFormat = ext.substring(1).toLowerCase();
        }

        if (autoExt && getExtension(output.getName()).isEmpty()) {
            output = new File(output.getAbsolutePath() + "." + officeFormat);
            System.err.println("ℹ️ Auto-extension applied: " + output.getAbsolutePath());
        }

        try {
            OpenCC opencc = new OpenCC(config);
            var result = OfficeHelper.convert(input, output, officeFormat, opencc, punct, keepFont);

            if (result.success) {
                System.err.println(result.message + "\n\uD83D\uDCC1 Output saved to: " + output.getAbsolutePath());
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
