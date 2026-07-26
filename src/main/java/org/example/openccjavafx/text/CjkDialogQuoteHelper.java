package org.example.openccjavafx.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides pure text operations for normalizing and validating quotation marks
 * commonly found in CJK dialog.
 */
public final class CjkDialogQuoteHelper {
    private CjkDialogQuoteHelper() {
    }

    /**
     * Normalizes ASCII dialog quotation marks according to the active curly or
     * Traditional Chinese corner-quote family.
     *
     * <p>Existing curly and corner quotation marks are preserved and update the
     * state used to interpret subsequent ASCII quotation marks. When an ASCII
     * quote opens a quotation with no active family, curly quotation marks are
     * used by default.</p>
     *
     * <p>When {@code preserveLatinApostrophes} is {@code true}, ASCII and curly
     * single quotation marks between ASCII Latin letters are preserved without
     * changing the current quote state. This protects apostrophes in words such
     * as {@code don't}, {@code I'm}, {@code rock'n'roll}, and {@code O'Brien}.</p>
     *
     * @param text the input text to normalize; may be {@code null}
     * @param preserveLatinApostrophes whether to preserve single quotation marks
     *                                 between ASCII Latin letters
     * @return the normalized text, or the original {@code null} or empty value
     */
    public static String normalizeDialogQuotes(String text, boolean preserveLatinApostrophes) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        DialogQuoteState state = new DialogQuoteState();
        StringBuilder normalized = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (preserveLatinApostrophes
                    && (character == '\'' || character == '‘' || character == '’')
                    && index > 0
                    && index + 1 < text.length()
                    && isAsciiLetter(text.charAt(index - 1))
                    && isAsciiLetter(text.charAt(index + 1))) {
                normalized.append(character);
            } else {
                normalized.append(state.normalize(character));
            }
        }
        return normalized.toString();
    }

    /**
     * Validates completed dialog quote pairs at the beginning and end of each
     * individual line.
     *
     * <p>The validator reports reversed pairs and pairs that mix quote families
     * or nesting levels. It checks only the first and last non-whitespace
     * characters and does not perform full multi-line quote balancing.</p>
     *
     * @param text the text whose dialog quotation marks should be inspected
     * @return a validation result containing every suspicious line
     */
    public static DialogQuoteValidationResult validateDialogQuotes(String text) {
        if (text == null || text.isEmpty()) {
            return new DialogQuoteValidationResult(List.of());
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<DialogQuoteIssue> issues = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String originalLine = lines[index];
            String stripped = originalLine.strip();
            if (!stripped.isEmpty() && hasSuspiciousCompletedPair(stripped)) {
                issues.add(new DialogQuoteIssue(index + 1, originalLine));
            }
        }
        return new DialogQuoteValidationResult(issues);
    }

    private static boolean hasSuspiciousCompletedPair(String stripped) {
        if (stripped.length() < 2) {
            return false;
        }
        char first = stripped.charAt(0);
        char last = stripped.charAt(stripped.length() - 1);
        if (isCloser(first) && isOpener(last)) {
            return true;
        }
        return isOpener(first) && isCloser(last) && !isMatchingPair(first, last);
    }

    private static boolean isOpener(char character) {
        return character == '“' || character == '‘' || character == '「' || character == '『';
    }

    private static boolean isCloser(char character) {
        return character == '”' || character == '’' || character == '」' || character == '』';
    }

    private static boolean isMatchingPair(char opener, char closer) {
        return (opener == '“' && closer == '”')
                || (opener == '‘' && closer == '’')
                || (opener == '「' && closer == '」')
                || (opener == '『' && closer == '』');
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
    }

    private enum QuoteFamily {
        NONE,
        CURLY,
        CORNER
    }

    private static final class DialogQuoteState {
        private QuoteFamily doubleFamily = QuoteFamily.NONE;
        private QuoteFamily singleFamily = QuoteFamily.NONE;

        private char normalize(char character) {
            return switch (character) {
                case '“' -> {
                    doubleFamily = QuoteFamily.CURLY;
                    yield character;
                }
                case '”', '」' -> {
                    doubleFamily = QuoteFamily.NONE;
                    yield character;
                }
                case '「' -> {
                    doubleFamily = QuoteFamily.CORNER;
                    yield character;
                }
                case '"' -> normalizeAsciiDouble();
                case '‘' -> {
                    singleFamily = QuoteFamily.CURLY;
                    yield character;
                }
                case '’', '』' -> {
                    singleFamily = QuoteFamily.NONE;
                    yield character;
                }
                case '『' -> {
                    singleFamily = QuoteFamily.CORNER;
                    yield character;
                }
                case '\'' -> normalizeAsciiSingle();
                default -> character;
            };
        }

        private char normalizeAsciiDouble() {
            if (doubleFamily == QuoteFamily.CURLY) {
                doubleFamily = QuoteFamily.NONE;
                return '”';
            }
            if (doubleFamily == QuoteFamily.CORNER) {
                doubleFamily = QuoteFamily.NONE;
                return '」';
            }
            doubleFamily = QuoteFamily.CURLY;
            return '“';
        }

        private char normalizeAsciiSingle() {
            if (singleFamily == QuoteFamily.CURLY) {
                singleFamily = QuoteFamily.NONE;
                return '’';
            }
            if (singleFamily == QuoteFamily.CORNER) {
                singleFamily = QuoteFamily.NONE;
                return '』';
            }
            singleFamily = QuoteFamily.CURLY;
            return '‘';
        }
    }
}
