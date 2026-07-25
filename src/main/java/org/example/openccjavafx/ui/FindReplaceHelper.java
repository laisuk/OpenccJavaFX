package org.example.openccjavafx.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pure string-based find/replace operations, independent of the JavaFX toolkit.
 */
public final class FindReplaceHelper {
    private FindReplaceHelper() {
    }

    public enum Outcome {FOUND, WRAPPED, NO_MATCH, INVALID_PATTERN, INVALID_REPLACEMENT, NOT_COMPLETE_MATCH}

public static final class FindResult {
        private final Outcome outcome;
        private final int start;
        private final int end;
        private final String error;

        public FindResult(Outcome outcome, int start, int end, String error) {
            this.outcome = outcome;
            this.start = start;
            this.end = end;
            this.error = error;
        }

        public Outcome outcome() {
            return outcome;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public String error() {
            return error;
        }

        public boolean found() {
            return outcome == Outcome.FOUND || outcome == Outcome.WRAPPED;
        }
    }

    public static final class ReplaceResult {
        private final Outcome outcome;
        private final String text;
        private final int count;
        private final int selectionStart;
        private final int selectionEnd;
        private final String error;

        public ReplaceResult(Outcome outcome, String text, int count,
                             int selectionStart, int selectionEnd, String error) {
            this.outcome = outcome;
            this.text = text;
            this.count = count;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.error = error;
        }

        public Outcome outcome() {
            return outcome;
        }

        public String text() {
            return text;
        }

        public int count() {
            return count;
        }

        public int selectionStart() {
            return selectionStart;
        }

        public int selectionEnd() {
            return selectionEnd;
        }

        public String error() {
            return error;
        }

        public boolean replaced() {
            return count > 0;
        }
    }

    private static final class FindPattern {
        private final Pattern pattern;
        private final FindResult failure;

        private FindPattern(Pattern pattern, FindResult failure) {
            this.pattern = pattern;
            this.failure = failure;
        }

        private Pattern pattern() {
            return pattern;
        }

        private FindResult failure() {
            return failure;
        }
    }

    public static FindResult findNext(String text, String findText, boolean matchCase,
                                      boolean regex, int selectionStart, int selectionEnd,
                                      int caret, boolean skipCurrentZeroLengthMatch) {
        FindPattern prepared = prepareFindPattern(findText, matchCase, regex);
        if (prepared.failure() != null) return prepared.failure();
        Pattern pattern = prepared.pattern();

        int from = clamp(selectionStart != selectionEnd ? selectionEnd : caret, 0, text.length());
        int skippedZeroPosition = skipCurrentZeroLengthMatch ? from : -1;
        if (skipCurrentZeroLengthMatch) from = nextCodePointIndex(text, from);
        Matcher matcher = pattern.matcher(text);
        if (skippedZeroPosition < text.length() && matcher.find(from)) return found(matcher, false);
        matcher.reset();
        while (from > 0 && matcher.find() && matcher.start() < from) {
            if (matcher.start() != skippedZeroPosition || matcher.end() != skippedZeroPosition) {
                return found(matcher, true);
            }
        }
        return noMatch();
    }

    public static FindResult findPrevious(String text, String findText, boolean matchCase,
                                          boolean regex, int selectionStart, int selectionEnd,
                                          int caret, boolean skipCurrentZeroLengthMatch) {
        FindPattern prepared = prepareFindPattern(findText, matchCase, regex);
        if (prepared.failure() != null) return prepared.failure();
        Pattern pattern = prepared.pattern();

        int boundary = clamp(selectionStart != selectionEnd ? selectionStart : caret, 0, text.length());
        Matcher matcher = pattern.matcher(text);
        FindResult previous = null;
        FindResult last = null;
        while (matcher.find()) {
            FindResult current = found(matcher, false);
            if (!skipCurrentZeroLengthMatch || matcher.start() != boundary || matcher.end() != boundary) {
                last = current;
            }
            boolean strictlyBefore = matcher.end() <= boundary
                    && (matcher.start() < boundary || matcher.end() < boundary);
            if (strictlyBefore) previous = current;
        }
        if (previous != null) return previous;
        if (last != null) return new FindResult(Outcome.WRAPPED, last.start(), last.end(), null);
        return noMatch();
    }

    public static ReplaceResult replaceCurrent(String text, String findText, String replacement,
                                               boolean matchCase, boolean regex,
                                               int selectionStart, int selectionEnd) {
        Pattern pattern;
        try {
            pattern = compile(findText, matchCase, regex);
        } catch (PatternSyntaxException ex) {
            return unchanged(Outcome.INVALID_PATTERN, text, ex.getDescription());
        }
        if (pattern == null) return unchanged(Outcome.NO_MATCH, text, null);

        int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
        int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.start() == start && matcher.end() == end) {
                try {
                    String actualReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);
                    StringBuffer prefixAndReplacement = new StringBuffer();
                    matcher.appendReplacement(prefixAndReplacement, actualReplacement);
                    String expanded = prefixAndReplacement.substring(start);
                    String result = text.substring(0, start) + expanded + text.substring(end);
                    return new ReplaceResult(Outcome.FOUND, result, 1, start, start + expanded.length(), null);
                } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                    return unchanged(Outcome.INVALID_REPLACEMENT, text, safeMessage(ex));
                }
            }
            if (matcher.start() > start) break;
        }
        return unchanged(Outcome.NOT_COMPLETE_MATCH, text, null);
    }

    public static ReplaceResult replaceAll(String text, String findText, String replacement,
                                           boolean matchCase, boolean regex) {
        Pattern pattern;
        try {
            pattern = compile(findText, matchCase, regex);
        } catch (PatternSyntaxException ex) {
            return unchanged(Outcome.INVALID_PATTERN, text, ex.getDescription());
        }
        if (pattern == null) return unchanged(Outcome.NO_MATCH, text, null);

        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        if (count == 0) return unchanged(Outcome.NO_MATCH, text, null);
        try {
            String actualReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);
            String result = pattern.matcher(text).replaceAll(actualReplacement);
            return new ReplaceResult(Outcome.FOUND, result, count, 0, 0, null);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return unchanged(Outcome.INVALID_REPLACEMENT, text, safeMessage(ex));
        }
    }

    private static Pattern compile(String findText, boolean matchCase, boolean regex) {
        if (findText == null || findText.isEmpty()) return null;
        String expression = regex ? findText : Pattern.quote(findText);
        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(expression, flags);
    }

    private static FindPattern prepareFindPattern(String findText, boolean matchCase, boolean regex) {
        try {
            Pattern pattern = compile(findText, matchCase, regex);
            return pattern == null
                    ? new FindPattern(null, noMatch())
                    : new FindPattern(pattern, null);
        } catch (PatternSyntaxException ex) {
            return new FindPattern(null, invalidPattern(ex));
        }
    }

    private static int nextCodePointIndex(String text, int index) {
        if (index >= text.length()) return text.length();
        return index + Character.charCount(text.codePointAt(index));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static FindResult found(Matcher matcher, boolean wrapped) {
        return new FindResult(wrapped ? Outcome.WRAPPED : Outcome.FOUND, matcher.start(), matcher.end(), null);
    }

    private static FindResult noMatch() {
        return new FindResult(Outcome.NO_MATCH, -1, -1, null);
    }

    private static FindResult invalidPattern(PatternSyntaxException ex) {
        return new FindResult(Outcome.INVALID_PATTERN, -1, -1, ex.getDescription());
    }

    private static ReplaceResult unchanged(Outcome outcome, String text, String error) {
        return new ReplaceResult(outcome, text, 0, -1, -1, error);
    }

    private static String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
