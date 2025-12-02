package pdfboxhelper;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small utility class wrapping PDFBox 3.x text extraction for OpenccJavaFX.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Extract full text from a PDF ({@link #extractText(File)})</li>
 *   <li>Extract page-by-page text as a list ({@link #extractTextPerPage(File)})</li>
 *   <li>Extract text with simple page headers
 *       ({@link #extractTextWithHeaders(File)})</li>
 *   <li>Strip common zero-width characters from extracted text</li>
 *   <li>Use {@code Loader.loadPDF(...)} and try-with-resources throughout</li>
 * </ul>
 *
 * <p>This class is Java 8 compatible and uses {@link java.util.logging.Logger}
 * for basic diagnostics.</p>
 */
public final class PdfBoxHelper {

    private static final Logger LOGGER = Logger.getLogger(PdfBoxHelper.class.getName());

    private PdfBoxHelper() {
        // Utility class – no instances allowed.
    }

    // ========================================================================
    // Zero-width character normalization
    // ========================================================================

    /**
     * Zero-width characters commonly found in extracted PDF text:
     * <ul>
     *   <li>U+200B ZERO WIDTH SPACE</li>
     *   <li>U+200C ZERO WIDTH NON-JOINER</li>
     *   <li>U+200D ZERO WIDTH JOINER</li>
     *   <li>U+FEFF ZERO WIDTH NO-BREAK SPACE (BOM)</li>
     * </ul>
     */
    private static final String ZERO_WIDTH_REGEX = "[\u200B\u200C\u200D\uFEFF]";

    /**
     * Removes common zero-width characters from the given text.
     *
     * @param text input text, may be {@code null}
     * @return cleaned text, or {@code null} if input was {@code null}
     */
    private static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll(ZERO_WIDTH_REGEX, "");
    }

    // ========================================================================
    // Core helpers
    // ========================================================================

    /**
     * Creates a {@link PDFTextStripper} instance with predictable defaults.
     *
     * <p>If you need to tune line/paragraph separators or spacing,
     * centralize that here so behaviour is consistent across all
     * extraction methods.</p>
     */
    private static PDFTextStripper createStripper() throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        // Keep defaults; you can tune these if necessary:
        // stripper.setLineSeparator("\n");
        // stripper.setParagraphStart("");
        // stripper.setParagraphEnd("\n\n");
        return stripper;
    }

    /**
     * Ensures the document is not encrypted. PDFBox requires additional
     * decryption handling for encrypted PDFs, which this helper does not
     * implement. For now, we fail fast with a clear error.
     *
     * @throws IOException if the document is encrypted
     */
    private static void ensureNotEncrypted(PDDocument doc) throws IOException {
        if (doc.isEncrypted()) {
            throw new IOException("Encrypted PDFs are not supported by PdfBoxHelper.");
        }
    }

    // ========================================================================
    // Public API – file based
    // ========================================================================

    /**
     * Extracts the full text of the given PDF file as a single string,
     * without page headers.
     *
     * @param file PDF file to read; must not be {@code null}
     * @return extracted text with zero-width characters stripped
     * @throws IOException if loading or parsing the PDF fails
     */
    public static String extractText(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return extractTextInternal(file, false);
    }

    /**
     * Extracts the full text of the given PDF file and adds a simple header
     * before each page:
     *
     * <pre>
     * === [Page 1/10] ===
     * ...page text...
     *
     * === [Page 2/10] ===
     * ...page text...
     * </pre>
     *
     * @param file PDF file to read; must not be {@code null}
     * @return extracted text with headers and zero-width characters stripped
     * @throws IOException if loading or parsing the PDF fails
     */
    public static String extractTextWithHeaders(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return extractTextInternal(file, true);
    }

    /**
     * Extracts the PDF text as a list of pages. Each entry in the returned
     * list corresponds to one page.
     *
     * @param file PDF file to read; must not be {@code null}
     * @return list of per-page text (never {@code null}, possibly empty)
     * @throws IOException if loading or parsing the PDF fails
     */
    public static List<String> extractTextPerPage(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");

        List<String> pages = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(file)) {
            ensureNotEncrypted(doc);

            PDFTextStripper stripper = createStripper();
            int total = doc.getNumberOfPages();

            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                pages.add(cleanText(text != null ? text : ""));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to extract text per page from PDF: " + file, e);
            throw e;
        }

        return pages;
    }

    /**
     * Internal helper used by {@link #extractText(File)} and
     * {@link #extractTextWithHeaders(File)}.
     *
     * @param file       PDF file to read
     * @param withHeader whether to add {@code === [Page x/n] ===} page markers
     */
    private static String extractTextInternal(File file, boolean withHeader) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            ensureNotEncrypted(doc);

            if (!withHeader) {
                // Simple "whole document" extraction
                PDFTextStripper stripper = createStripper();
                String text = stripper.getText(doc);
                return cleanText(text);
            }

            // Per-page extraction with headers
            PDFTextStripper stripper = createStripper();
            int total = doc.getNumberOfPages();
            StringBuilder sb = new StringBuilder(8192);

            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);

                String pageText = stripper.getText(doc);
                pageText = cleanText(pageText != null ? pageText.trim() : "");

                // Skip completely empty pages (optional; adjust if you want)
                // if (pageText.isEmpty()) {
                //     continue;
                // }

                if (sb.length() != 0) {
                    sb.append(System.lineSeparator()).append(System.lineSeparator());
                }

                sb.append("=== [Page ")
                        .append(i)
                        .append("/")
                        .append(total)
                        .append("] ===")
                        .append(System.lineSeparator())
                        .append(System.lineSeparator())
                        .append(pageText);
            }

            return sb.toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to extract text from PDF: " + file, e);
            throw e;
        }
    }

    // ========================================================================
    // Public API – byte[] based
    // ========================================================================

    /**
     * Extracts full text from a PDF represented as a byte array.
     *
     * @param pdfBytes bytes of a PDF document; must not be {@code null}
     * @return extracted text with zero-width characters stripped
     * @throws IOException if loading or parsing the PDF fails
     */
    public static String extractText(byte[] pdfBytes) throws IOException {
        Objects.requireNonNull(pdfBytes, "pdfBytes must not be null");

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            ensureNotEncrypted(doc);

            PDFTextStripper stripper = createStripper();
            String text = stripper.getText(doc);
            return cleanText(text);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to extract text from PDF bytes", e);
            throw e;
        }
    }

    // ========================================================================
    // Write UTF-8 text to file
    // ========================================================================

    /**
     * Writes the given text to the specified file using UTF-8 encoding.
     *
     * @param text   text content to write (never null)
     * @param output target file (never null)
     * @throws IOException if writing fails
     */
    public static void saveTextToFile(String text, File output) throws IOException {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(output, "output must not be null");

        // Java 8: no Files.writeString, so write bytes directly
        try {
            Files.write(output.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to write PDF text to file: " + output, e);
            throw e;
        }
    }

}
