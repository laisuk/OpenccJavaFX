package org.example.openccjavafx.text;

import java.util.ArrayList;
import java.util.List;

/** Pure text operations for normalizing and validating CJK dialog quotes. */
public final class CjkDialogQuoteHelper {
    private CjkDialogQuoteHelper() {
    }

    public static String normalizeDialogQuotes(String text, boolean preserveLatinApostrophes) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        DialogQuoteState state = new DialogQuoteState();
        StringBuilder normalized = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (preserveLatinApostrophes
                    && character == '\''
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
