package pdfboxhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * CJK-aware paragraph reflow helper for PDF-extracted text.
 *
 * <p>Ported from the C# PdfHelper.ReflowCjkParagraphs implementation
 * used in ZhoConverterGui, adapted to Java 8. All logic preserved exactly.</p>
 */
public final class PdfReflowHelper {

    // ======================================================================
    // Configuration
    // ======================================================================

    /**
     * CJK sentence-ending punctuation characters
     */
    private static final char[] CJK_PUNCT_END_CHARS = {
            '。', '！', '？', '；', '：', '…', '—', '”', '」', '’', '』', '.',
            '）', '】', '》', '〗', '〔', '〉', '」', '』', '］', '｝',
    };

    /**
     * Chapter / heading detection
     */
    private static final Pattern TITLE_HEADING_REGEX = Pattern.compile(
            "^(?=.{0,60}$)(前言|序章|终章|尾声|后记|番外|尾聲|後記|第.{0,10}?([章节部卷節回]))"
    );

    /**
     * Lines with 2+ leading ASCII/full-width spaces are considered indented
     */
    private static final Pattern INDENT_REGEX = Pattern.compile("^[\\s\u3000]{2,}");

    /**
     * Dialog opening characters
     */
    private static final String DIALOG_OPENERS = "“‘「『";

    /**
     * Bracket sets
     */
    private static final String OPEN_BRACKETS = "（([【《";
    private static final String CLOSE_BRACKETS = "）)]】》";

    private PdfReflowHelper() {
    }

    // ======================================================================
    // Public API
    // ======================================================================

    /**
     * Reflows CJK text extracted from PDF.
     *
     * @param text             raw text extracted from PDF
     * @param addPdfPageHeader whether to keep PDF page headers (=== [Page 1/10] ===)
     * @param compact          true = "p1\np2\np3", false = "p1\n\np2\n\np3"
     */
    public static String reflowCjkParagraphs(String text, boolean addPdfPageHeader, boolean compact) {
        Objects.requireNonNull(text, "text must not be null");

        if (text.isEmpty() || text.trim().isEmpty()) {
            return "";
        }

        // Normalize CRLF → LF
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // Split with limit to preserve empty lines
        String[] lines = text.split("\n", -1);

        List<String> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        DialogState dialogState = new DialogState();

        for (String rawLine : lines) {

            // 1) Visual form: trim right, remove half-width indent
            String stripped = trimEnd(rawLine);
            stripped = stripHalfWidthIndentKeepFullWidth(stripped);

            // 2) Logical form for heading detection
            String headingProbe = trimStartSpacesAndFullWidth(stripped);
            boolean isTitleHeading = TITLE_HEADING_REGEX.matcher(headingProbe).find();

            // Style-layer repeated titles
            if (isTitleHeading) {
                stripped = collapseRepeatedSegments(stripped);
            }

            // --- Empty line ---
            if (stripped.isEmpty()) {

                if (!addPdfPageHeader && buffer.length() > 0) {
                    char lastChar = buffer.charAt(buffer.length() - 1);
                    // Page-break-like empty line
                    if (indexOf(lastChar) < 0) {
                        continue;
                    }
                }

                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }
                continue;
            }

            // --- Page markers ---
            if (stripped.startsWith("=== ") && stripped.endsWith("===")) {
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }
                segments.add(stripped);
                continue;
            }

            // --- Titles ---
//            if (isTitleHeading) {
//                if (buffer.length() > 0) {
//                    segments.add(buffer.toString());
//                    buffer.setLength(0);
//                    dialogState.reset();
//                }
//                segments.add(stripped);
//                continue;
//            }
            // --- Titles / heading-like short lines (including numeric headings like "1", "007") ---
            if (isTitleHeading || isHeadingLike(stripped)) {
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }
                // 章節標題 / "1" / "2" / "第X章 夜色" 等 → 獨立段落
                segments.add(stripped);
                continue;
            }

            // Check dialog start
            boolean currentIsDialogStart = isDialogStarter(stripped);

            if (buffer.length() == 0) {
                // Start new paragraph
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            String bufferText = buffer.toString();

            // --- Dialog starter → force new paragraph ---
            if (currentIsDialogStart) {
                segments.add(bufferText);
                buffer.setLength(0);
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- Colon + dialog continuation ---
            if (bufferText.endsWith("：") || bufferText.endsWith(":")) {
                if (stripped.length() > 0 && DIALOG_OPENERS.indexOf(stripped.charAt(0)) >= 0) {
                    buffer.append(stripped);
                    dialogState.update(stripped);
                    continue;
                }
            }

            // --- CJK punctuation → paragraph end ---
            if (!bufferText.isEmpty()
                    && indexOf(bufferText.charAt(bufferText.length() - 1)) >= 0
                    && !dialogState.isUnclosed()) {

                segments.add(bufferText);
                buffer.setLength(0);
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- Previous is heading-like ---
//            if (isHeadingLike(bufferText)) {
//                segments.add(bufferText);
//                buffer.setLength(0);
//                buffer.append(stripped);
//                dialogState.reset();
//                dialogState.update(stripped);
//                continue;
//            }

            // --- Indentation → new paragraph ---
            if (INDENT_REGEX.matcher(rawLine).find()) {
                segments.add(bufferText);
                buffer.setLength(0);
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- Chapter-like short endings ---
            if (bufferText.length() <= 12 &&
                    Pattern.compile("([章节部卷節回])[】》〗〕〉」』）]*$")
                            .matcher(bufferText).find()) {

                segments.add(bufferText);
                buffer.setLength(0);
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- Default: soft join ---
            buffer.append(stripped);
            dialogState.update(stripped);
        }

        // Flush last buffer
        if (buffer.length() > 0) {
            segments.add(buffer.toString());
        }

        String joiner = compact ? "\n" : "\n\n";
        return String.join(joiner, segments);
    }

    /**
     * Default: novel mode (with blank line between paragraphs).
     */
    public static String reflowCjkParagraphs(String text, boolean addPdfPageHeader) {
        return reflowCjkParagraphs(text, addPdfPageHeader, false);
    }

    // ======================================================================
    // DialogState
    // ======================================================================

    private static final class DialogState {
        private int doubleQuote;
        private int singleQuote;
        private int corner;
        private int cornerBold;

        void reset() {
            doubleQuote = 0;
            singleQuote = 0;
            corner = 0;
            cornerBold = 0;
        }

        void update(String s) {
            if (s == null || s.isEmpty()) return;

            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '“':
                        doubleQuote++;
                        break;
                    case '”':
                        if (doubleQuote > 0) doubleQuote--;
                        break;

                    case '‘':
                        singleQuote++;
                        break;
                    case '’':
                        if (singleQuote > 0) singleQuote--;
                        break;

                    case '「':
                        corner++;
                        break;
                    case '」':
                        if (corner > 0) corner--;
                        break;

                    case '『':
                        cornerBold++;
                        break;
                    case '』':
                        if (cornerBold > 0) cornerBold--;
                        break;

                    default:
                        break;
                }
            }
        }

        boolean isUnclosed() {
            return doubleQuote > 0 || singleQuote > 0 || corner > 0 || cornerBold > 0;
        }
    }

    // ======================================================================
    // Helper functions (ported from C#)
    // ======================================================================

    private static boolean isDialogStarter(String s) {
        if (s == null) return false;
        s = trimStartSpacesAndFullWidth(s);
        return !s.isEmpty() && DIALOG_OPENERS.indexOf(s.charAt(0)) >= 0;
    }

    private static boolean isHeadingLike(String s) {
        if (s == null) return false;

        s = s.trim();
        if (s.isEmpty()) return false;

        // keep page markers intact
        if (s.startsWith("=== ") && s.endsWith("===")) {
            return false;
        }

        // If *ends* with CJK punctuation → not heading
        char last = s.charAt(s.length() - 1);
        if (indexOf(last) >= 0) { // uses CJK_PUNCT_END_CHARS
            return false;
        }

        // Reject headings with unclosed brackets
        if (hasUnclosedBracket(s)) {
            return false;
        }

        int len = s.length();

        // Short line heuristics (<= 15 chars)
        if (len <= 15) {

            boolean hasNonAscii = false;
            boolean allAscii = true;
            boolean hasLetter = false;
            boolean allAsciiDigits = true;

            for (int i = 0; i < len; i++) {
                char ch = s.charAt(i);

                if (ch > 0x7F) {
                    hasNonAscii = true;
                    allAscii = false;
                    allAsciiDigits = false;
                    continue;
                }

                if (!Character.isDigit(ch)) {
                    allAsciiDigits = false;
                }

                if (Character.isLetter(ch)) {
                    hasLetter = true;
                }
            }

            // Re-read last (we didn't modify s)
            last = s.charAt(len - 1);

            // Rule C: pure ASCII digits → heading
            if (allAsciiDigits) {
                return true;
            }

            // Rule A: CJK/mixed short line, not ending with comma
            if (hasNonAscii && last != '，' && last != ',') {
                return true;
            }

            // Rule B: pure ASCII short line with at least one letter
            return allAscii && hasLetter;
        }

        return false;
    }

    private static boolean hasUnclosedBracket(String s) {
        boolean hasOpen = false;
        boolean hasClose = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!hasOpen && OPEN_BRACKETS.indexOf(ch) >= 0) {
                hasOpen = true;
            }
            if (!hasClose && CLOSE_BRACKETS.indexOf(ch) >= 0) {
                hasClose = true;
            }
            if (hasOpen && hasClose) break;
        }

        return hasOpen && !hasClose;
    }

    private static String stripHalfWidthIndentKeepFullWidth(String s) {
        if (s == null || s.isEmpty()) return s;
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return s.substring(i);
    }

    private static String trimStartSpacesAndFullWidth(String s) {
        if (s == null || s.isEmpty()) return s;
        int start = 0;
        while (start < s.length()) {
            char ch = s.charAt(start);
            if (ch == ' ' || ch == '\u3000') {
                start++;
            } else {
                break;
            }
        }
        return s.substring(start);
    }

    private static String collapseRepeatedSegments(String line) {
        if (line == null || line.isEmpty()) return line;

        String[] parts = line.trim().split("[ \t]+");
        if (parts.length == 0) return line;

        for (int i = 0; i < parts.length; i++) {
            parts[i] = collapseRepeatedToken(parts[i]);
        }

        return String.join(" ", parts);
    }

    private static String collapseRepeatedToken(String token) {
        if (token == null) return null;

        int len = token.length();
        if (len < 4 || len > 200) return token;

        for (int unitLen = 2; unitLen <= 20 && unitLen <= len / 2; unitLen++) {
            if (len % unitLen != 0) continue;

            String unit = token.substring(0, unitLen);
            boolean allMatch = true;

            for (int pos = 0; pos < len; pos += unitLen) {
                if (!token.regionMatches(pos, unit, 0, unitLen)) {
                    allMatch = false;
                    break;
                }
            }

            if (allMatch) return unit;
        }
        return token;
    }

    private static String trimEnd(String s) {
        if (s == null || s.isEmpty()) return s;
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    private static int indexOf(char ch) {
        for (int i = 0; i < CJK_PUNCT_END_CHARS.length; i++) {
            if (CJK_PUNCT_END_CHARS[i] == ch) return i;
        }
        return -1;
    }
}
