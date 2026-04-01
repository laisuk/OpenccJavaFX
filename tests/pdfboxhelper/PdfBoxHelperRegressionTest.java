package pdfboxhelper;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxHelperRegressionTest {

    private static final Path PDF_FIXTURE = Paths.get("tests", "JiaMianYouXi.pdf");
    private static final Path EXPECTED_REFLOWED = Paths.get("tests", "JiaMianYouXi_extracted.txt");
    private static final Path INDENT_FIXTURE = Paths.get("tests", "CHUNK_ABC.pdf");

    @Test
    void extractAndReflowMatchesReferenceOutput() throws IOException {
        File pdf = PDF_FIXTURE.toFile();
        assertTrue(pdf.isFile(), "Missing PDF fixture: " + PDF_FIXTURE.toAbsolutePath());
        assertTrue(Files.isRegularFile(EXPECTED_REFLOWED),
                "Missing expected output fixture: " + EXPECTED_REFLOWED.toAbsolutePath());

        AtomicInteger progressCalls = new AtomicInteger();
        AtomicInteger lastPage = new AtomicInteger();
        AtomicInteger totalPages = new AtomicInteger();

        String raw = PdfBoxHelper.extractText(pdf, false, (current, total) -> {
            progressCalls.incrementAndGet();
            lastPage.set(current);
            totalPages.set(total);
        });

        String actual = PdfReflowHelper.reflowCjkParagraphs(raw, false, false);
        String expected = Files.readString(EXPECTED_REFLOWED, StandardCharsets.UTF_8);

        assertEquals(normalizeNewlines(expected), normalizeNewlines(actual),
                "Reflowed output diverged from tests/JiaMianYouXi_extracted.txt");
        assertTrue(totalPages.get() > 0, "Expected progress callback to report total pages");
        assertEquals(totalPages.get(), progressCalls.get(),
                "Expected one progress callback per extracted page");
        assertEquals(totalPages.get(), lastPage.get(),
                "Expected the last progress callback to report the final page");
    }

    @Test
    void progressCallbackDoesNotChangeExtractedText() throws IOException {
        File pdf = INDENT_FIXTURE.toFile();
        assertTrue(pdf.isFile(), "Missing PDF fixture: " + INDENT_FIXTURE.toAbsolutePath());

        String plain = PdfBoxHelper.extractText(pdf);
        AtomicInteger progressCalls = new AtomicInteger();
        AtomicInteger totalPages = new AtomicInteger();

        String withProgress = PdfBoxHelper.extractText(pdf, false, (current, total) -> {
            progressCalls.incrementAndGet();
            totalPages.set(total);
        });

        assertEquals(normalizeNewlines(plain), normalizeNewlines(withProgress),
                "Progress-enabled extraction should not alter extracted text");
        assertTrue(totalPages.get() > 0, "Expected progress callback to report total pages");
        assertEquals(totalPages.get(), progressCalls.get(),
                "Expected one progress callback per extracted page");
    }

    @Test
    void progressCallbackReportsSequentialPageNumbers() throws IOException {
        File pdf = INDENT_FIXTURE.toFile();
        assertTrue(pdf.isFile(), "Missing PDF fixture: " + INDENT_FIXTURE.toAbsolutePath());

        StringBuilder seen = new StringBuilder();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger lastTotal = new AtomicInteger();

        PdfBoxHelper.extractText(pdf, false, (current, total) -> {
            calls.incrementAndGet();
            lastTotal.set(total);
            if (seen.length() != 0) {
                seen.append(',');
            }
            seen.append(current).append('/').append(total);
        });

        assertTrue(lastTotal.get() > 0, "Expected progress callback to report total pages");
        assertEquals(lastTotal.get(), calls.get(),
                "Expected one progress callback per page");

        StringBuilder expected = new StringBuilder();
        for (int page = 1; page <= lastTotal.get(); page++) {
            if (expected.length() != 0) {
                expected.append(',');
            }
            expected.append(page).append('/').append(lastTotal.get());
        }

        assertEquals(expected.toString(), seen.toString(),
                "Progress callback should report sequential 1-based page numbers with stable total pages");
    }

    @Test
    void emptyPageRemainsVisibleWithoutHeaders() {
        StringBuilder sb = new StringBuilder();

        PdfBoxHelper.appendExtractedPage(sb, "Page one\n", 1, 3, false);
        PdfBoxHelper.appendExtractedPage(sb, "   \n\t", 2, 3, false);
        PdfBoxHelper.appendExtractedPage(sb, "Page three\n", 3, 3, false);

        assertEquals("Page one\n\nPage three\n", normalizeNewlines(sb.toString()),
                "Blank extracted pages should leave a visible separator in no-header mode");
    }

    @Test
    void emptyPageStillGetsHeaderWhenHeadersEnabled() {
        StringBuilder sb = new StringBuilder();

        PdfBoxHelper.appendExtractedPage(sb, "   \n\t", 2, 5, true);

        assertEquals("=== [Page 2/5] ===\n\n", normalizeNewlines(sb.toString()),
                "Blank extracted pages should still emit a page header when headers are enabled");
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
