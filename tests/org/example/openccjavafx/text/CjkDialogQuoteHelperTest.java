package org.example.openccjavafx.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CjkDialogQuoteHelperTest {
    @Test
    void normalizesAsciiQuotesAndUsesActiveFamilies() {
        assertEquals("“Hello”", normalize("\"Hello\""));
        assertEquals("‘Hello’", normalize("'Hello'"));
        assertEquals("“Hello”", normalize("“Hello\""));
        assertEquals("‘Hello’", normalize("‘Hello'"));
        assertEquals("「Hello」", normalize("「Hello\""));
        assertEquals("『Hello』", normalize("『Hello'"));
    }

    @Test
    void preservesExistingCornerQuotesAndUnrelatedText() {
        assertEquals("「你好」與『世界』", normalize("「你好」與『世界』"));
        assertEquals("沒有引號。", normalize("沒有引號。"));
        assertEquals("", normalize(""));
    }

    @Test
    void preservesLatinApostrophes() {
        String input = "I'm reading O'Brien's book. don't rock'n'roll I’m rock‘n’roll";
        assertEquals(input, normalize(input));
    }

    @Test
    void tracksOuterAndInnerStatesIndependentlyAcrossLines() {
        assertEquals("「He said ‘hello’」\n“Next”", normalize("「He said 'hello'\"\n\"Next\""));
    }

    @Test
    void canTreatLatinApostrophesAsDialogQuotesWhenRequested() {
        assertEquals("don‘t", CjkDialogQuoteHelper.normalizeDialogQuotes("don't", false));
    }

    @Test
    void detectsReversedPairsAndPreservesIndentation() {
        DialogQuoteValidationResult result = validate("  ”Hello“  \n\t」你好「");
        assertEquals(2, result.suspiciousLines().size());
        assertEquals(new DialogQuoteIssue(1, "  ”Hello“  "), result.suspiciousLines().get(0));
        assertEquals(new DialogQuoteIssue(2, "\t」你好「"), result.suspiciousLines().get(1));
    }

    @Test
    void detectsMixedFamiliesAndLevels() {
        DialogQuoteValidationResult result = validate("「Hello”\n“Hello」\n『Hello’\n‘Hello』\n「Hello』\n『Hello」\n“Hello’\n‘Hello”");
        assertEquals(8, result.suspiciousLines().size());
    }

    @Test
    void acceptsAllMatchingPairsAndIgnoresBlankLines() {
        DialogQuoteValidationResult result = validate("“Hello”\n\n‘Hello’\n  \t\n「你好」\n『你好』");
        assertTrue(result.isValid());
        assertTrue(result.firstIssue().isEmpty());
    }

    @Test
    void keepsLogicalLineNumbersForEveryNewlineForm() {
        assertEquals(3, validate("ok\r\nok\r\n”bad“").firstIssue().orElseThrow().lineNumber());
        assertEquals(3, validate("ok\rok\r”bad“").firstIssue().orElseThrow().lineNumber());
    }

    @Test
    void returnsMultipleIssuesInSourceOrderAndFirstIssue() {
        DialogQuoteValidationResult result = validate("”one“\nvalid\n「two”");
        assertEquals(List.of(1, 3), result.suspiciousLines().stream().map(DialogQuoteIssue::lineNumber).toList());
        assertEquals(1, result.firstIssue().orElseThrow().lineNumber());
    }

    @Test
    void buildsEstablishedSummaries() {
        assertEquals("No suspicious dialog quote issues found.", validate("“ok”").buildSummary());
        String summary = validate("”one“\n「two”").buildSummary();
        assertTrue(summary.contains("Found 2 suspicious dialog quote line(s)."));
        assertTrue(summary.contains("missing, extra, reversed, or mixed dialog quote"));
        assertTrue(summary.contains("reported line or a few lines above it"));
        assertTrue(summary.contains("Fix the source text and validate again."));
    }

    @Test
    void defensivelyCopiesAndExposesAnImmutableIssueList() {
        ArrayList<DialogQuoteIssue> supplied = new ArrayList<>();
        supplied.add(new DialogQuoteIssue(1, "”bad“"));
        DialogQuoteValidationResult result = new DialogQuoteValidationResult(supplied);
        supplied.clear();
        assertEquals(1, result.suspiciousLines().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.suspiciousLines().add(new DialogQuoteIssue(2, "」bad「")));
    }

    private static String normalize(String text) {
        return CjkDialogQuoteHelper.normalizeDialogQuotes(text, true);
    }

    private static DialogQuoteValidationResult validate(String text) {
        return CjkDialogQuoteHelper.validateDialogQuotes(text);
    }
}
