package org.example.openccjavafx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoToLineHelperTest {
    @Test
    void acceptsFirstAndFinalLinesAndConvertsToZeroBasedIndex() {
        GoToLineHelper.Result first = GoToLineHelper.validate("1", 566);
        GoToLineHelper.Result last = GoToLineHelper.validate("566", 566);

        assertTrue(first.valid());
        assertEquals(0, first.paragraphIndex());
        assertTrue(last.valid());
        assertEquals(565, last.paragraphIndex());
    }

    @Test
    void acceptsSurroundingWhitespace() {
        GoToLineHelper.Result result = GoToLineHelper.validate("  12  ", 20);
        assertTrue(result.valid());
        assertEquals(11, result.paragraphIndex());
    }

    @Test
    void rejectsEmptyAndWhitespaceOnlyInput() {
        assertInvalid("Enter a line number", GoToLineHelper.validate("", 10));
        assertInvalid("Enter a line number", GoToLineHelper.validate("   ", 10));
    }

    @Test
    void rejectsZeroAndNegativeInput() {
        assertInvalid("Line number must be positive", GoToLineHelper.validate("0", 10));
        assertInvalid("Line number must be positive", GoToLineHelper.validate("-2", 10));
    }

    @Test
    void rejectsNonNumericInputAndIntegerOverflow() {
        assertInvalid("Invalid line number", GoToLineHelper.validate("line 2", 10));
        assertInvalid("Invalid line number", GoToLineHelper.validate("99999999999999999999", 10));
    }

    @Test
    void rejectsLineGreaterThanParagraphCountWithoutClamping() {
        assertInvalid("Line must be between 1 and 566", GoToLineHelper.validate("567", 566));
    }

    @Test
    void treatsTheSingleEmptyParagraphAsLineOne() {
        GoToLineHelper.Result result = GoToLineHelper.validate("1", 1);
        assertTrue(result.valid());
        assertEquals(0, result.paragraphIndex());
    }

    private static void assertInvalid(String message, GoToLineHelper.Result result) {
        assertFalse(result.valid());
        assertEquals(-1, result.paragraphIndex());
        assertEquals(message, result.message());
    }
}
