package pdfboxhelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Punctuation tables and small scanning helpers for CJK PDF text reflow.
 */
public class PunctSets {
    /**
     * Creates a punctuation helper.
     */
    public PunctSets() {
    }

    /**
     * Mutable out-param for a char (Java replacement for C# out char).
     */
    public static final class CharRef {
        /**
         * Creates a holder with the default null character value.
         */
        public CharRef() {
        }

        /**
         * Referenced character value.
         */
        public char value;
    }

    /**
     * Mutable out-params for (index, char).
     */
    public static final class IndexCharRef {
        /**
         * Creates a holder with default index and character values.
         */
        public IndexCharRef() {
        }

        /**
         * Referenced character index.
         */
        public int index;
        /**
         * Referenced character value.
         */
        public char ch;
    }

    /**
     * CJK sentence-ending punctuation characters
     */
    private static final char[] CJK_PUNCT_END_CHARS = {
            // Standard CJK sentence-ending punctuation
            '。', '！', '？', '；', '：', '…', '—',

            // Closing quotes (CJK)
            '”', '’', '」', '』',

            // Chinese / full-width closing brackets
            '）', '】', '》', '〗', '〕', '］', '｝',

            // Angle brackets (CJK + ASCII)
            '＞', '〉', '>',

            // Allowed ASCII-like endings
            '.', ')', ':', '!', '?'
    };

    private static final boolean[] CJK_PUNCT_END_TABLE = new boolean[65536];

    /**
     * Checks whether a character is accepted as clause or sentence-ending punctuation.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} is in the CJK end-punctuation table
     */
    public static boolean isClauseOrEndPunct(char ch) {
        return CJK_PUNCT_END_TABLE[ch];
    }

    /**
     * Dialog opening characters
     */
    private static final String DIALOG_OPENERS = "“‘「『﹁﹃";

    /**
     * Dialog closing characters
     * <p>
     * IMPORTANT:
     * Order and pairing MUST stay consistent with DIALOG_OPENERS.
     */
    private static final String DIALOG_CLOSERS = "”’」』﹂﹄";
    private static final boolean[] DIALOG_OPENER_TABLE = new boolean[Character.MAX_VALUE + 1];
    private static final boolean[] DIALOG_CLOSER_TABLE = new boolean[Character.MAX_VALUE + 1];

    // -------------------------
    // Soft continuation punctuation
    // -------------------------
    private static final boolean[] COMMA_LIKE_TABLE = new boolean[Character.MAX_VALUE + 1];

    static {
        COMMA_LIKE_TABLE['，'] = true; // full-width comma
        COMMA_LIKE_TABLE[','] = true; // ASCII comma
        COMMA_LIKE_TABLE['、'] = true; // ideographic comma
    }

    /**
     * Checks whether a character is comma-like for soft continuation logic.
     *
     * @param ch character to inspect
     * @return {@code true} for ASCII comma, full-width comma, or ideographic comma
     */
    public static boolean isCommaLike(char ch) {
        return COMMA_LIKE_TABLE[ch];
    }

    /**
     * Returns true if the string contains any comma-like character.
     * Null or empty strings return false.
     *
     * @param s string to inspect
     * @return {@code true} when {@code s} contains a comma-like character
     */
    public static boolean containsAnyCommaLike(String s) {
        if (s == null || s.isEmpty())
            return false;

        for (int i = 0; i < s.length(); i++) {
            if (COMMA_LIKE_TABLE[s.charAt(i)])
                return true;
        }
        return false;
    }

    /**
     * Checks whether a character is an ASCII or full-width colon.
     *
     * @param ch character to inspect
     * @return {@code true} for {@code ':'} or {@code '：'}
     */
    public static boolean isColonLike(char ch) {
        return ch == '：' || ch == ':';
    }

    /**
     * Returns true if the string ends with a colon-like character (':' or '：'),
     * ignoring trailing whitespace.
     *
     * @param s string to inspect
     * @return {@code true} when the last non-whitespace character is colon-like
     */
    public static boolean endsWithColonLike(String s) {
        if (s == null || s.isEmpty())
            return false;

        for (int i = s.length() - 1; i >= 0; i--) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch))
                continue;
            return isColonLike(ch);
        }
        return false;
    }

    /**
     * Returns true if the string ends with an ellipsis,
     * ignoring trailing whitespace.
     * <p>
     * Recognized forms are:
     * <ul>
     *   <li>{@code …}</li>
     *   <li>{@code ……}</li>
     *   <li>{@code ...}</li>
     *   <li>{@code ..}</li>
     * </ul>
     *
     * @param s string to inspect
     * @return {@code true} when the last non-whitespace characters form
     * a supported ellipsis
     */
    public static boolean endsWithEllipsis(String s) {
        if (s == null || s.isEmpty())
            return false;

        int last = -1;

        // Find last non-whitespace character.
        for (int i = s.length() - 1; i >= 0; i--) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch))
                continue;

            last = i;

            if (ch == '…')
                return true;

            break;
        }

        if (last <= 0)
            return false;

        return s.charAt(last) == '.'
                && s.charAt(last - 1) == '.';
    }

    // ---------------------------------------------------------------------
    // Bracket punctuations (open → close)
    // ---------------------------------------------------------------------

    private static final boolean[] OPEN_BRACKET_TABLE = new boolean[Character.MAX_VALUE + 1];
    private static final boolean[] CLOSE_BRACKET_TABLE = new boolean[Character.MAX_VALUE + 1];
    private static final char[] BRACKET_CLOSE_BY_OPEN = new char[Character.MAX_VALUE + 1];

    // Metadata key-value separators
    /**
     * Characters accepted as metadata key-value separators.
     */
    public static final char[] METADATA_SEPARATORS = new char[]{
            '：', // full-width colon
            ':',  // ASCII colon
            '　', // full-width ideographic space (U+3000)
            '·',  // Middle dot (Latin)
            '・'  // Katakana middle dot
    };

    static {
        // init CJK punct table
        for (char c : CJK_PUNCT_END_CHARS)
            CJK_PUNCT_END_TABLE[c] = true;

        for (int i = 0; i < DIALOG_OPENERS.length(); i++) {
            DIALOG_OPENER_TABLE[DIALOG_OPENERS.charAt(i)] = true;
        }
        for (int i = 0; i < DIALOG_CLOSERS.length(); i++) {
            DIALOG_CLOSER_TABLE[DIALOG_CLOSERS.charAt(i)] = true;
        }

        // init bracket pairs
        Map<Character, Character> map = new HashMap<>();

        // Parentheses
        map.put('（', '）');
        map.put('(', ')');

        // Square brackets
        map.put('[', ']');
        map.put('［', '］');

        // Curly braces (ASCII + FULLWIDTH)
        map.put('{', '}');
        map.put('｛', '｝');

        // Angle brackets
        map.put('<', '>');
        map.put('＜', '＞');
        map.put('〈', '〉');

        // CJK brackets
        map.put('【', '】');
        map.put('《', '》');
        map.put('〔', '〕');
        map.put('〖', '〗');

        for (Map.Entry<Character, Character> e : map.entrySet()) {
            char o = e.getKey();
            char c = e.getValue();
            OPEN_BRACKET_TABLE[o] = true;
            CLOSE_BRACKET_TABLE[c] = true;
            BRACKET_CLOSE_BY_OPEN[o] = c;
        }
    }

    /**
     * Checks whether a character opens a dialog quote.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} is a configured dialog opener
     */
    public static boolean isDialogOpener(char ch) {
        return DIALOG_OPENER_TABLE[ch];
    }

    /**
     * Checks whether a character closes a dialog quote.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} is a configured dialog closer
     */
    public static boolean isDialogCloser(char ch) {
        return DIALOG_CLOSER_TABLE[ch];
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Checks whether a character is a supported bracket opener.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} has a configured matching closer
     */
    public static boolean isBracketOpener(char ch) {
        return OPEN_BRACKET_TABLE[ch];
    }

    /**
     * Checks whether a character is a supported bracket closer.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} is a configured closer
     */
    public static boolean isBracketCloser(char ch) {
        return CLOSE_BRACKET_TABLE[ch];
    }

    /**
     * Checks whether two characters are a configured bracket pair.
     *
     * @param open  opening bracket candidate
     * @param close closing bracket candidate
     * @return {@code true} when {@code close} matches {@code open}
     */
    public static boolean isMatchingBracket(char open, char close) {
        return BRACKET_CLOSE_BY_OPEN[open] == close;
    }

    /**
     * Finds the configured closer for an opening bracket.
     *
     * @param open opening bracket character
     * @return matching closer, or {@code 0} when {@code open} is unknown
     */
    public static char tryGetMatchingCloser(char open) {
        // Return 0 when unknown.
        return BRACKET_CLOSE_BY_OPEN[open];
    }

    /**
     * Checks whether a closing parenthesis is allowed after sentence punctuation.
     *
     * @param ch character to inspect
     * @return {@code true} for ASCII or full-width closing parenthesis
     */
    public static boolean isAllowedPostfixCloser(char ch) {
        return ch == '）' || ch == ')';
    }

    /**
     * Returns true if the string ends with an allowed postfix closer
     * ')' or '）', ignoring trailing whitespace.
     *
     * @param s string to inspect
     * @return {@code true} when the last non-whitespace character is an allowed postfix closer
     */
    public static boolean endsWithAllowedPostfixCloser(String s) {
        if (s == null || s.isEmpty())
            return false;

        for (int i = s.length() - 1; i >= 0; i--) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch))
                continue;
            return isAllowedPostfixCloser(ch);
        }
        return false;
    }

    /**
     * Checks whether the first non-whitespace character starts dialog text.
     *
     * @param s string to inspect
     * @return {@code true} when the first non-whitespace character is a dialog opener
     */
    public static boolean isDialogStarter(String s) {
        if (s == null || s.isEmpty())
            return false;

        int idx = PunctSets.indexOfFirstNonWhitespace(s);
        return idx >= 0 && isDialogOpener(s.charAt(idx));
    }

    /**
     * Checks whether the string ends with a dialog closer after trailing whitespace.
     *
     * @param s string to inspect
     * @return {@code true} when the last non-whitespace character is a dialog closer
     */
    public static boolean endsWithDialogCloser(String s) {
        CharRef lastRef = new CharRef();

        return tryGetLastNonWhitespace(s, lastRef)
                && isDialogCloser(lastRef.value);
    }

    /**
     * Detects unmatched or mismatched brackets in a string.
     *
     * @param s string to inspect
     * @return {@code true} when a bracket is unclosed, stray, or mismatched
     */
    public static boolean hasUnclosedBracket(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }

        return hasUnclosedBracket(s, 0, s.length());
    }

    /**
     * Detects unmatched or mismatched brackets in {@code s[start, end)}.
     *
     * @param s     string to inspect
     * @param start inclusive start index
     * @param end   exclusive end index
     * @return {@code true} when a bracket is unclosed, stray, or mismatched
     */
    static boolean hasUnclosedBracket(String s, int start, int end) {
        if (s == null || start >= end) {
            return false;
        }

        boolean seenBracket = false;

        // Small fast stack (like ArrayPool rent 16)
        char[] stack = null;
        int top = 0;

        for (int i = start; i < end; i++) {
            char ch = s.charAt(i);

            if (isBracketOpener(ch)) {
                seenBracket = true;

                if (stack == null) {
                    stack = new char[16];
                } else if (top == stack.length) {
                    char[] bigger = new char[stack.length * 2];
                    System.arraycopy(stack, 0, bigger, 0, stack.length);
                    stack = bigger;
                }

                stack[top++] = ch;
                continue;
            }

            if (!isBracketCloser(ch)) {
                continue;
            }

            seenBracket = true;

            // Stray closer.
            if (top == 0) {
                return true;
            }

            char open = stack[--top];

            // Mismatched pair.
            if (!isMatchingBracket(open, ch)) {
                return true;
            }
        }

        // Unclosed opener(s) only matter if we saw any bracket at all.
        return seenBracket && top != 0;
    }

    /**
     * Detects unmatched dialog quote characters.
     *
     * @param s text to inspect
     * @return {@code true} when a dialog opener or closer is unbalanced
     */
    public static boolean hasUnclosedDialogQuote(CharSequence s) {
        if (s == null || s.length() == 0) {
            return false;
        }

        int[] balance = new int[DIALOG_OPENERS.length()];

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            int openIndex = DIALOG_OPENERS.indexOf(ch);
            if (openIndex >= 0) {
                balance[openIndex]++;
                continue;
            }

            int closeIndex = DIALOG_CLOSERS.indexOf(ch);
            if (closeIndex >= 0) {
                if (balance[closeIndex] > 0) {
                    balance[closeIndex]--;
                } else {
                    return true; // dangling closer on this line
                }
            }
        }

        for (int n : balance) {
            if (n > 0) {
                return true; // unclosed opener remains
            }
        }

        return false;
    }

    /**
     * Detects visual separator / divider lines such as:
     * ──────
     * ======
     * ------
     * or mixed variants (e.g. ───===───).
     *
     * <p>This method is intended to run on a <b>probe</b> string
     * (indentation already removed). Whitespace is ignored.</p>
     *
     * <p>These lines represent layout boundaries and must always
     * force paragraph breaks during reflow.</p>
     *
     * @param s probe string to inspect
     * @return {@code true} when the probe is a visual divider line
     */
    public static boolean isVisualDividerLine(String s) {
        if (s == null)
            return false;

        int first = indexOfFirstNonWhitespace(s);
        if (first < 0)
            return false;

        int total = 0;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // Ignore whitespace completely (probe may still contain gaps)
            if (Character.isWhitespace(ch))
                continue;

            total++;

            // Unicode box drawing block (U+2500–U+257F)
            if (ch >= '─' && ch <= '╿')
                continue;

            // ASCII visual separators (common in TXT / OCR)
            if (ch == '-' || ch == '=' || ch == '_' || ch == '~' || ch == '～')
                continue;

            // Star / asterisk-based visual dividers
            if (ch == '*' || ch == '＊' || ch == '★' || ch == '☆')
                continue;

            // Any real text → not a pure visual divider
            return false;
        }

        // Require minimal visual length to avoid accidental triggers
        return total >= 3;
    }

    /**
     * Checks whether a character is a strong sentence-ending punctuation mark.
     *
     * @param ch character to inspect
     * @return {@code true} for CJK or ASCII exclamation/question/full-stop sentence endings
     */
    public static boolean isStrongSentenceEnd(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?';
    }

    /**
     * Checks whether a character closes a quote.
     *
     * @param ch character to inspect
     * @return {@code true} when {@code ch} is a configured dialog closer
     */
    public static boolean isQuoteCloser(char ch) {
        // Rust: is_dialog_closer(ch)
        return isDialogCloser(ch);
    }

    // -------------------------
    // Common helper (optional)
    // -------------------------

    /**
     * Finds the last non-whitespace character.
     *
     * @param s   string to inspect
     * @param out output holder for the character
     * @return true if found; writes last non-whitespace char into out. value
     */
    public static boolean tryGetLastNonWhitespace(String s, CharRef out) {
        if (s == null || s.isEmpty()) {
            if (out != null) out.value = '\0';
            return false;
        }

        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            out.value = c;
            return true;
        }

        out.value = '\0';
        return false;
    }

    /**
     * Finds the last non-whitespace character and its index.
     *
     * @param s   string to inspect
     * @param out output holder for the index and character
     * @return true if found; writes index+char into out.index/out.ch
     */
    public static boolean tryGetLastNonWhitespace(String s, IndexCharRef out) {
        if (s == null || s.isEmpty()) {
            if (out != null) {
                out.index = -1;
                out.ch = '\0';
            }
            return false;
        }

        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            out.index = i;
            out.ch = c;
            return true;
        }

        out.index = -1;
        out.ch = '\0';
        return false;
    }

    /**
     * Finds the first non-whitespace character index.
     *
     * @param s string to inspect
     * @return index of first non-whitespace char, or -1 if none
     */
    public static int indexOfFirstNonWhitespace(String s) {
        if (s == null || s.isEmpty())
            return -1;

        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c))
                return i;
        }
        return -1;
    }

    /**
     * Finds the first non-whitespace character.
     *
     * @param s   string to inspect
     * @param out output holder for the character
     * @return true if found; writes first non-whitespace char into out. value
     */
    public static boolean tryGetFirstNonWhitespace(String s, CharRef out) {
        int idx = indexOfFirstNonWhitespace(s);
        if (idx >= 0) {
            out.value = s.charAt(idx);
            return true;
        }
        out.value = '\0';
        return false;
    }

    /**
     * Finds previous non-whitespace char strictly before startIndex.
     * Example: startIndex = s.length() => scans whole string backwards.
     *
     * @param s           string to inspect
     * @param beforeIndex exclusive index before which scanning starts
     * @param out         output holder for the character
     * @return {@code true} when a previous non-whitespace character is found
     */
    public static boolean tryGetPrevNonWhitespace(String s, int beforeIndex, CharRef out) {
        if (s == null || s.isEmpty()) {
            if (out != null) out.value = '\0';
            return false;
        }

        int i = Math.min(beforeIndex - 1, s.length() - 1);
        for (; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            out.value = c;
            return true;
        }

        out.value = '\0';
        return false;
    }

    /**
     * Finds previous non-whitespace char strictly before beforeIndex.
     * Writes index+char into out.index/out.ch.
     *
     * @param s           string to inspect
     * @param beforeIndex exclusive index before which scanning starts
     * @param out         output holder for the index and character
     * @return {@code true} when a previous non-whitespace character is found
     */
    public static boolean tryGetPrevNonWhitespace(String s, int beforeIndex, IndexCharRef out) {
        if (s == null || s.isEmpty()) {
            if (out != null) {
                out.index = -1;
                out.ch = '\0';
            }
            return false;
        }

        int i = Math.min(beforeIndex - 1, s.length() - 1);
        for (; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            out.index = i;
            out.ch = c;
            return true;
        }

        out.index = -1;
        out.ch = '\0';
        return false;
    }
}
