package openccjava;

import java.util.*;

public class PunctSets {
    /**
     * Mutable out-param for a char (Java replacement for C# out char).
     */
    public static final class CharRef {
        public char value;
    }

    /**
     * Mutable out-params for (index, char).
     */
    public static final class IndexCharRef {
        public int index;
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

    // -------------------------
    // Soft continuation punctuation
    // -------------------------
    private static final boolean[] COMMA_LIKE_TABLE = new boolean[Character.MAX_VALUE + 1];

    static {
        COMMA_LIKE_TABLE['，'] = true; // full-width comma
        COMMA_LIKE_TABLE[','] = true; // ASCII comma
        COMMA_LIKE_TABLE['、'] = true; // ideographic comma
    }

    public static boolean isCommaLike(char ch) {
        return COMMA_LIKE_TABLE[ch];
    }

    // ---------------------------------------------------------------------
    // Bracket punctuations (open → close)
    // ---------------------------------------------------------------------

    private static final boolean[] OPEN_BRACKET_TABLE = new boolean[Character.MAX_VALUE + 1];
    private static final boolean[] CLOSE_BRACKET_TABLE = new boolean[Character.MAX_VALUE + 1];
    private static final char[] BRACKET_CLOSE_BY_OPEN = new char[Character.MAX_VALUE + 1];

    // Metadata key-value separators
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

    public static boolean isCjkPunctEnd(char ch) {
        return CJK_PUNCT_END_TABLE[ch];
    }

    public static boolean isDialogOpener(char ch) {
        return DIALOG_OPENERS.indexOf(ch) >= 0;
    }

    private static boolean isDialogCloser(char ch) {
        return DIALOG_CLOSERS.indexOf(ch) >= 0;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    public static boolean isBracketOpener(char ch) {
        return OPEN_BRACKET_TABLE[ch];
    }

    public static boolean isBracketCloser(char ch) {
        return CLOSE_BRACKET_TABLE[ch];
    }

    public static boolean isMatchingBracket(char open, char close) {
        return BRACKET_CLOSE_BY_OPEN[open] == close;
    }

    public static boolean isDialogStarter(String s) {
        if (s == null || s.isEmpty())
            return false;

        int idx = PunctSets.indexOfFirstNonWhitespace(s);
        return idx >= 0 && isDialogOpener(s.charAt(idx));
    }

    public static boolean hasUnclosedBracket(String s) {
        if (s == null || s.isEmpty())
            return false;

        boolean seenBracket = false;

        // Small fast stack (like ArrayPool rent 16)
        char[] stack = null;
        int top = 0;

        for (int i = 0, n = s.length(); i < n; i++) {
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

            if (!isBracketCloser(ch))
                continue;

            seenBracket = true;

            // stray closer
            if (top == 0)
                return true;

            char open = stack[--top];

            // mismatch
            if (!isMatchingBracket(open, ch))
                return true;
        }

        // Unclosed opener(s) only matters if we saw any bracket at all
        return seenBracket && top != 0;
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
     */
    public static boolean isBoxDrawingLine(String s) {
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

    public static boolean isStrongSentenceEnd(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?';
    }

    public static boolean isQuoteCloser(char ch) {
        // Rust: is_dialog_closer(ch)
        return isDialogCloser(ch);
    }

    // -------------------------
    // Common helper (optional)
    // -------------------------

    /**
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
     */
    public static boolean tryGetPrevNonWhitespace(String s, int startIndex, CharRef out) {
        if (s == null || s.isEmpty()) {
            if (out != null) out.value = '\0';
            return false;
        }

        int i = Math.min(startIndex - 1, s.length() - 1);
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
