package pdfboxhelper;

public class CjkText {
    public static String trimEnd(String s) {
        if (s == null || s.isEmpty()) return s;
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    public static int indexOfAny(String text, char[] chars) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        final int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            for (char c : chars) {
                if (ch == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return (end == s.length()) ? s : s.substring(0, end);
    }

    @SuppressWarnings("unused")
    private static int indexOfChar(char[] array, char ch) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isAllAscii(String s) {
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) > 0x7F)
                return false;
        return true;
    }

    /**
     * Minimal CJK checker (BMP focused).
     * Designed for heading / structure heuristics, not full Unicode linguistics.
     */
    public static boolean isCjk(char ch) {

        // CJK Unified Ideographs Extension A (U+3400–U+4DBF)
        if ((int) ch >= 0x3400 && (int) ch <= 0x4DBF)
            return true;

        // CJK Unified Ideographs (U+4E00–U+9FFF)
        if ((int) ch >= 0x4E00 && (int) ch <= 0x9FFF)
            return true;

        // CJK Compatibility Ideographs (U+F900–U+FAFF)
        return (int) ch >= 0xF900 && (int) ch <= 0xFAFF;
    }

    // Returns true if the string consists entirely of CJK characters.
    // Whitespace handling is controlled by allowWhitespace.
    // Returns false for null, empty, or whitespace-only strings.
    private static boolean isAllCjk(String s, boolean allowWhitespace) {
        if (s == null || s.isEmpty())
            return false;

        boolean seen = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (Character.isWhitespace(ch)) {
                if (!allowWhitespace)
                    return false;
                continue;
            }

            seen = true;

            if (!isCjk(ch))
                return false;
        }

        return seen;
    }

    public static boolean isAllCjkIgnoringWhitespace(String s) {
        return isAllCjk(s, true);
    }

    public static boolean isAllCjkNoWhiteSpace(String s) {
        return isAllCjk(s, false);
    }

    /**
     * Returns true if the string contains BOTH:
     * - CJK (as defined by isCjk(ch)), and
     * - ASCII all number (A-Z, a-z 0-9) OR full-width digits (０-９),
     * while rejecting any other characters except neutral ASCII separators:
     * space, '-', '/', ':', '.'
     */
    public static boolean isMixedCjkAscii(String s) {
        boolean hasCjk = false;
        boolean hasAscii = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // Neutral ASCII (allowed, but doesn't count as ASCII content)
            if (ch == ' ' || ch == '-' || ch == '/' || ch == ':' || ch == '.')
                continue;

            if (ch <= 0x7F) {
                if (Character.isLetterOrDigit(ch)) {
                    hasAscii = true;
                } else {
                    return false;
                }
            } else if (ch >= '０' && ch <= '９') { // Full-width digits
                hasAscii = true;
            } else if (isCjk(ch)) { // You already have this helper in your codebase
                hasCjk = true;
            } else {
                return false;
            }

            if (hasCjk && hasAscii)
                return true;
        }

        return false;
    }

    /**
     * Returns true if the string is mostly CJK:
     * - Ignores whitespace
     * - Ignores digits (ASCII + FULLWIDTH)
     * - Counts CJK characters
     * - Counts ASCII letters only (punctuation is neutral)
     * <p>
     * Rule:
     * cjk > 0 && cjk >= asciiLetters
     * <p>
     * Designed for heading / structure heuristics.
     */
    public static boolean isMostlyCjk(String s) {
        if (s == null || s.isEmpty())
            return false;

        int cjk = 0;
        int ascii = 0;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // Neutral whitespace
            if (Character.isWhitespace(ch))
                continue;

            // Neutral digits (ASCII + FULLWIDTH)
            if (isDigitAsciiOrFullWidth(ch))
                continue;

            if (isCjk(ch)) {
                cjk++;
                continue;
            }

            // Count ASCII letters only; ASCII punctuation is neutral
            if (ch <= 0x7F && Character.isLetter(ch)) {
                ascii++;
            }
        }

        return cjk > 0 && cjk >= ascii;
    }

    private static boolean isDigitAsciiOrFullWidth(char ch) {
        // ASCII digits '0'–'9'
        if (ch >= '0' && ch <= '9')
            return true;

        // FULLWIDTH digits '０'–'９'
        return ch >= '０' && ch <= '９';
    }

    @SuppressWarnings("unused")
    private static int findLastNonWhitespaceIndex(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    // ------ Sentence Boundary start ------ //

    public static boolean endsWithSentenceBoundary(String s) {
        if (s == null) return false;

        // s.trim().isEmpty()
        if (s.trim().isEmpty()) {
            return false;
        }

        int lastNonWs = findLastNonWhitespaceCharIndex(s);
        if (lastNonWs < 0) {
            return false;
        }

        char last = s.charAt(lastNonWs);

        // 1) Strong sentence end-ers.
        if (PunctSets.isStrongSentenceEnd(last)) {
            return true;
        }

        // 2) Level-2 accepts OCR '.' / ':' at line end (mostly-CJK).
        if ((last == '.' || last == ':') && isOcrCjkAsciiPunctAtLineEnd(s, lastNonWs)) {
            return true;
        }

        // 3) Quote closers after strong end, plus OCR artifact `.“”` / `.」` / `.）`.
        if (PunctSets.isQuoteCloser(last) || PunctSets.isAllowedPostfixCloser(last)) {
            int prevNonWs = findPrevNonWhitespaceCharIndex(s, lastNonWs);
            if (prevNonWs >= 0) {
                char prev = s.charAt(prevNonWs);

                // Strong end immediately before quote closer.
                if (PunctSets.isStrongSentenceEnd(prev)) {
                    return true;
                }

                // OCR artifact: ASCII '.' before closers.
                if (prev == '.' && isOcrCjkAsciiPunctBeforeClosers(s, prevNonWs)) {
                    return true;
                }

                // Optional: enable if you want ':' before closers too.
                // if (prev == ':' && isOcrCjkAsciiPunctBeforeClosers(s, prevNonWs)) return true;
            }
        }

        // 4) Bracket closers with mostly CJK. (reserved)
//        if (PunctSets.isBracketCloser(last) && lastNonWs > 0 && isMostlyCjk(s)) {
//            return true;
//        }

        // 5) NEW: long Mostly-CJK line ending with full-width colon "："
        // Treat as a weak boundary (common in novels: "他说：" then dialog starts next line)
        if (PunctSets.isColonLike(last) && isMostlyCjk(s)) {
            return true;
        }

        // 6) Ellipsis as weak boundary.
        return endsWithEllipsis(s);
    }

    /**
     * Last non-whitespace char index (char index).
     */
    private static int findLastNonWhitespaceCharIndex(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Previous non-whitespace char index strictly before endExclusive (char index).
     */
    private static int findPrevNonWhitespaceCharIndex(String s, int endExclusive) {
        for (int i = endExclusive - 1; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Strict OCR: punct itself is at end-of-line (only whitespace after it),
     * and preceded by CJK in a mostly-CJK line.
     */
    private static boolean isOcrCjkAsciiPunctAtLineEnd(String s, int punctIndex) {
        if (punctIndex <= 0) return false;
        if (!isAtLineEndIgnoringWhitespace(s, punctIndex)) return false;
        char prev = s.charAt(punctIndex - 1);
        return isCjk(prev) && isMostlyCjk(s);
    }

    /**
     * Relaxed OCR: after punct, allow only whitespace and closers (quote/bracket).
     * Enables `.“”` / `.」` / `.）` to count as sentence boundary.
     */
    private static boolean isOcrCjkAsciiPunctBeforeClosers(String s, int punctIndex) {
        if (punctIndex <= 0) return false;
        if (!isAtEndAllowingClosers(s, punctIndex)) return false;
        char prev = s.charAt(punctIndex - 1);
        return isCjk(prev) && isMostlyCjk(s);
    }

    private static boolean isAtLineEndIgnoringWhitespace(String s, int index) {
        for (int i = index + 1; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAtEndAllowingClosers(String s, int index) {
        for (int i = index + 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            if (PunctSets.isQuoteCloser(ch) || PunctSets.isBracketCloser(ch)) continue;
            return false;
        }
        return true;
    }

    private static boolean endsWithEllipsis(String s) {
        String t = trimEnd(s);
        return t.endsWith("…") || t.endsWith("……") || t.endsWith("...") || t.endsWith("..");
    }
}
