package org.example.openccjavafx.ui;

import org.junit.jupiter.api.Test;

import static org.example.openccjavafx.ui.FindReplaceHelper.Outcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FindReplaceHelperTest {
    @Test
    void findsLiteralNextAndTreatsMetacharactersLiterally() {
        FindReplaceHelper.FindResult result = FindReplaceHelper.findNext("a $\\()[]{} b", "$\\()[]{}", true, false, 0, 0, 0, false);
        assertEquals(FOUND, result.outcome());
        assertEquals("$\\()[]{}", "a $\\()[]{} b".substring(result.start(), result.end()));
    }

    @Test
    void honorsMatchCase() {
        assertEquals(NO_MATCH, FindReplaceHelper.findNext("Alpha", "alpha", true, false, 0, 0, 0, false).outcome());
        assertEquals(FOUND, FindReplaceHelper.findNext("Alpha", "alpha", false, false, 0, 0, 0, false).outcome());
    }

    @Test
    void findsRegexAndWrapsNext() {
        FindReplaceHelper.FindResult regex = FindReplaceHelper.findNext("one 123 two", "\\d+", true, true, 0, 0, 0, false);
        assertEquals("123", "one 123 two".substring(regex.start(), regex.end()));
        FindReplaceHelper.FindResult wrapped = FindReplaceHelper.findNext("one two one", "one", true, false, 8, 11, 11, false);
        assertEquals(WRAPPED, wrapped.outcome());
        assertEquals(0, wrapped.start());
    }

    @Test
    void wrapsPreviousToLastMatch() {
        FindReplaceHelper.FindResult result = FindReplaceHelper.findPrevious("one two one", "one", true, false, 0, 3, 3, false);
        assertEquals(WRAPPED, result.outcome());
        assertEquals(8, result.start());
    }

    @Test
    void literalReplacementPreservesDollarAndBackslash() {
        FindReplaceHelper.ReplaceResult result = FindReplaceHelper.replaceAll("x x", "x", "$1\\path", true, false);
        assertEquals("$1\\path $1\\path", result.text());
        assertEquals(2, result.count());
    }

    @Test
    void supportsNumberedAndNamedRegexGroups() {
        FindReplaceHelper.ReplaceResult numbered = FindReplaceHelper.replaceAll("<h1 id='x'>Title</h1>", "<h1([^>]*)>(.*?)</h1>", "<h2$1>$2</h2>", true, true);
        assertEquals("<h2 id='x'>Title</h2>", numbered.text());
        FindReplaceHelper.ReplaceResult named = FindReplaceHelper.replaceAll("<h1>Title</h1>", "(?<open><h1[^>]*>)(?<text>.*?)(?<close></h1>)", "${open}${text}</h2>", true, true);
        assertEquals("<h1>Title</h2>", named.text());
    }

    @Test
    void invalidPatternAndReplacementLeaveTextUnchanged() {
        FindReplaceHelper.ReplaceResult pattern = FindReplaceHelper.replaceAll("abc", "[", "x", true, true);
        assertEquals(INVALID_PATTERN, pattern.outcome());
        assertEquals("abc", pattern.text());
        FindReplaceHelper.ReplaceResult replacement = FindReplaceHelper.replaceAll("abc", "(a)", "$2", true, true);
        assertEquals(INVALID_REPLACEMENT, replacement.outcome());
        assertEquals("abc", replacement.text());
    }

    @Test
    void replaceAllReportsExactCount() {
        FindReplaceHelper.ReplaceResult result = FindReplaceHelper.replaceAll("cat cat cat", "cat", "dog", true, false);
        assertEquals(3, result.count());
        assertEquals("dog dog dog", result.text());
    }

    @Test
    void zeroLengthSearchAdvancesWithoutSplittingSupplementaryCharacter() {
        String text = "😀foo foo";
        FindReplaceHelper.FindResult first = FindReplaceHelper.findNext(text, "(?=foo)", true, true, 0, 0, 0, false);
        assertEquals(2, first.start());
        FindReplaceHelper.FindResult second = FindReplaceHelper.findNext(text, "(?=foo)", true, true, first.start(), first.end(), first.end(), true);
        assertEquals(6, second.start());
        FindReplaceHelper.FindResult afterEmoji = FindReplaceHelper.findNext(text, "(?=.)", true, true, 0, 0, 0, true);
        assertEquals(2, afterEmoji.start());
    }

    @Test
    void soleZeroLengthAnchorDoesNotRepeatForever() {
        FindReplaceHelper.FindResult first = FindReplaceHelper.findNext("abc", "^", true, true, 0, 0, 0, false);
        assertEquals(0, first.start());
        FindReplaceHelper.FindResult second = FindReplaceHelper.findNext("abc", "^", true, true,
                first.start(), first.end(), first.end(), true);
        assertEquals(NO_MATCH, second.outcome());

        FindReplaceHelper.FindResult end = FindReplaceHelper.findNext("abc", "$", true, true, 0, 0, 0, false);
        FindReplaceHelper.FindResult afterEnd = FindReplaceHelper.findNext("abc", "$", true, true,
                end.start(), end.end(), end.end(), true);
        assertEquals(NO_MATCH, afterEnd.outcome());

        FindReplaceHelper.FindResult previous = FindReplaceHelper.findPrevious("abc", "^", true, true,
                first.start(), first.end(), first.end(), true);
        assertEquals(NO_MATCH, previous.outcome());
    }

    @Test
    void replaceCurrentRequiresCompleteSelectedMatch() {
        FindReplaceHelper.ReplaceResult rejected = FindReplaceHelper.replaceCurrent("abc123", "\\d+", "x", true, true, 3, 5);
        assertEquals(NOT_COMPLETE_MATCH, rejected.outcome());
        assertEquals("abc123", rejected.text());
        FindReplaceHelper.ReplaceResult replaced = FindReplaceHelper.replaceCurrent("abc123", "(\\d+)", "[$1]", true, true, 3, 6);
        assertEquals("abc[123]", replaced.text());
    }
}
