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

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
