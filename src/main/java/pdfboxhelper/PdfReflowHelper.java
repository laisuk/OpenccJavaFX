package pdfboxhelper;

import java.util.*;
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
            '）', '】', '》', '〗', '〔', '〉', '」', '』', '］', '｝', ':', ')', '!'
    };

    /**
     * Chapter / heading detection
     */
    private static final Pattern TITLE_HEADING_REGEX = Pattern.compile(
            "(?x)^ (?=.{0,50}$)(前言|序章|终章|尾声|后记|番外.{0,10}?|尾聲|後記|.{0,10}?第.{0,5}?([章节部卷節回][^分合]).{0,20}?)"
    );

    /**
     * Lines with 2+ leading ASCII/full-width spaces are considered indented
     */
    private static final Pattern INDENT_REGEX = Pattern.compile("^[\\s\u3000]{2,}");

    /**
     * Dialog opening characters
     */
    private static final String DIALOG_OPENERS = "“‘「『";

    private static boolean isDialogOpener(char ch) {
        return DIALOG_OPENERS.indexOf(ch) >= 0;
    }

    /**
     * Bracket sets
     */
    private static final String OPEN_BRACKETS = "（([【《{<";
    private static final String CLOSE_BRACKETS = "）)]】》}>";

    // Metadata key-value separators
    private static final char[] METADATA_SEPARATORS = new char[]{
            '：', // full-width colon
            ':', // ASCII colon
            '・',
            '　' // full-width ideographic space (U+3000)
    };

    private static final Set<String> METADATA_KEYS = new HashSet<>(
            Arrays.asList(
                    // ===== 1. Title / Author / Publishing =====
                    "書名", "书名",
                    "作者",
                    "譯者", "译者",
                    "校訂", "校订",
                    "出版社",
                    "出版時間", "出版时间",
                    "出版日期",

                    // ===== 2. Copyright / License =====
                    "版權", "版权",
                    "版權頁", "版权页",
                    "版權信息", "版权信息",

                    // ===== 3. Editor / Pricing =====
                    "責任編輯", "责任编辑",
                    "編輯", "编辑",
                    "責編", "责编",
                    "定價", "定价",

                    // ===== 4. Descriptions / Forewords =====
                    // "內容簡介", "内容简介",
                    // "作者簡介", "作者简介",
                    "前言",
                    "序章",
                    "簡介", "简介",
                    "終章", "终章",
                    "尾聲", "尾声",
                    "後記", "后记",

                    // ===== 5. Digital Publishing =====
                    "品牌方",
                    "出品方",
                    "授權方", "授权方",
                    "電子版權", "数字版权",
                    "掃描", "扫描",
                    "OCR",

                    // ===== 6. CIP / Cataloging =====
                    "CIP",
                    "在版編目", "在版编目",
                    "分類號", "分类号",
                    "主題詞", "主题词",

                    // ===== 7. Publishing Cycle =====
                    "發行日", "发行日",
                    "初版",

                    // ===== 8. Common keys without variants =====
                    "ISBN"
            )
    );

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

            // 2) Probe form (for structural / heading detection): remove all indentation
            String probe = trimStartSpacesAndFullWidth(stripped);

            // 🧱 ABSOLUTE STRUCTURAL RULE — must be first (run on probe, output stripped)
            if (isBoxDrawingLine(probe)) {
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }

                segments.add(stripped);
                continue;
            }

            stripped = collapseRepeatedSegments(stripped);

            // 3) Logical form for heading detection
            String headingProbe = trimStartSpacesAndFullWidth(stripped);
            boolean isTitleHeading = TITLE_HEADING_REGEX.matcher(headingProbe).find();
            boolean isShortHeading = isHeadingLike(stripped);
            boolean isMetadata = isMetadataLine(stripped);

            // --- Empty line ---
            if (stripped.isEmpty()) {

                if (!addPdfPageHeader && buffer.length() > 0) {
                    char lastChar = buffer.charAt(buffer.length() - 1);
                    // Page-break-like empty line
                    if (indexOfChar(CJK_PUNCT_END_CHARS, lastChar) < 0) {
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

            // --- Titles (force flushing) ---
            if (isTitleHeading) {
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }
                segments.add(stripped);
                continue;
            }

            // 3b) Metadata
            if (isMetadata) {
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }

                // Metadata 每行獨立存放（之後你可以決定係 skip、折疊、顯示）
                segments.add(stripped);
                continue;
            }

            // 3c) Weak heading-like: only active when previous paragraph is "safe" AND looks ended.
            if (isShortHeading) {

                final boolean allCjk = isAllCjkIgnoringWhitespace(stripped);

                // Decide if current short line should become a standalone heading (and cause a split)
                boolean splitAsHeading;

                if (buffer.length() == 0) {
                    // file start / just flushed -> allow heading alone
                    splitAsHeading = true;
                } else {
                    final String bufText = buffer.toString();

                    if (hasUnclosedBracket(bufText)) {
                        // previous paragraph is "unsafe" -> must treat as continuation
                        splitAsHeading = false;
                    } else {
                        final String bt = rtrim(bufText);

                        if (bt.isEmpty()) {
                            // buffer has only whitespace -> treat like no previous paragraph
                            splitAsHeading = true;
                        } else {
                            final char last = bt.charAt(bt.length() - 1);

                            // previous ends with comma -> continuation
                            if (last == '，' || last == ',') {
                                splitAsHeading = false;
                            }
                            // all-CJK short heading line + previous not ended by sentence punctuation -> continuation
                            else splitAsHeading = !allCjk || indexOfChar(CJK_PUNCT_END_CHARS, last) >= 0;
                        }
                    }
                }

                if (splitAsHeading) {
                    if (buffer.length() > 0) {
                        segments.add(buffer.toString());
                        buffer.setLength(0);
                        dialogState.reset();
                    }
                    segments.add(stripped);
                    continue;
                }

                // else: fall through -> normal merge logic below
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

            // 🔸 NEW RULE: If previous line ends with comma,
            //     do NOT flush even if this line starts dialog.
            //     (comma-ending means the sentence is not finished)
            if (currentIsDialogStart) {
                // previous paragraph exists?
                if (!bufferText.isEmpty()) {
                    String trimmed = rtrim(bufferText);
                    char last = trimmed.isEmpty() ? '\0' : trimmed.charAt(trimmed.length() - 1);

                    // Comma-ending means sentence continues -> do NOT flush
                    boolean prevEndsWithCommaLike = (last == '，' || last == ',' || last == '、');

                    if (!prevEndsWithCommaLike) {
                        // flush previous paragraph, start dialog paragraph
                        segments.add(bufferText);
                        buffer.setLength(0);
                    }
                }

                // append current dialog start to buffer (either after flush or as continuation)
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
                    && indexOfChar(CJK_PUNCT_END_CHARS, bufferText.charAt(bufferText.length() - 1)) >= 0
                    && !dialogState.isUnclosed()) {

                segments.add(bufferText);
                buffer.setLength(0);
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

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
                    Pattern.compile("([章节部卷節回])[】》〗〕〉」』）}]*$")
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

        // Reject headings with unclosed brackets
        if (hasUnclosedBracket(s)) {
            return false;
        }

        int len = s.length();
        int maxLen = isAllAscii(s) ? 16 : 8;
        char last = s.charAt(len - 1);
        // Short circuit for item title-like: "物品准备："
        if ((last == ':' || last == '：') && len <= maxLen && isAllCjk(s.substring(0, len - 1))) {
            return true;
        }
        // If *ends* with CJK punctuation → not heading
        if (indexOfChar(CJK_PUNCT_END_CHARS, last) >= 0) { // uses CJK_PUNCT_END_CHARS
            return false;
        }

        // Short line heuristics (<= 15 chars)
        if (len <= maxLen) {

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

    static boolean isMetadataLine(String line) {
        if (line == null) {
            return false;
        }

        // A) whitespace / blank
        if (line.trim().isEmpty()) {
            return false;
        }

        // B) length limit
        if (line.length() > 30) {
            return false;
        }

        // C) find first separator
        int idx = indexOfAny(line, METADATA_SEPARATORS);
        if (idx <= 0 || idx > 10) {
            return false;
        }

        // D) extract key
        String key = line.substring(0, idx).trim();
        if (!METADATA_KEYS.contains(key)) {
            return false;
        }

        // E) get next non-space character
        int j = idx + 1;
        while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
            j++;
        }
        if (j >= line.length()) {
            return false;
        }

        // F) must NOT be dialog opener
        return !isDialogOpener(line.charAt(j));
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
    private static boolean isBoxDrawingLine(String s) {
        if (s == null || s.trim().isEmpty())
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
     * Style-layer repeat collapse for PDF headings / title lines.
     * <p>
     * Conceptually similar to the regex:
     * (.{4,10}?)\1{2,3}
     * <p>
     * but implemented with token- and phrase-aware logic so that
     * CJK headings such as:
     * <p>
     * "背负着一切的麒麟 背负着一切的麒麟 背负着一切的麒麟 背负着一切的麒麟"
     * <p>
     * collapse cleanly to a single phrase.
     * <p>
     * This also avoids collapsing natural text such as "哈哈哈哈哈哈"
     * by enforcing a base-unit length of 4–10 and at least 3 repeats.
     */
    private static String collapseRepeatedSegments(String line) {
        if (line == null || line.isEmpty())
            return line;

        // split by whitespace
        String[] parts = line.trim().split("[ \t]+");
        if (parts.length == 0)
            return line;

        // 1) collapse repeated *word sequences*
        parts = collapseRepeatedWordSequences(parts);

        // 2) collapse repeated patterns *inside a token*
        //    only if unitLen is between 4..10 and N >= 3
        for (int i = 0; i < parts.length; i++) {
            parts[i] = collapseRepeatedToken(parts[i]);
        }

        return String.join(" ", parts);
    }


    /**
     * Collapses repeated sequences of tokens (phrases).
     * <p>
     * Example:
     * ["背负着一切的麒麟", "背负着一切的麒麟", "背负着一切的麒麟", "背负着一切的麒麟"]
     * becomes:
     * ["背负着一切的麒麟"]
     * <p>
     * Very conservative & safe.
     */
    private static String[] collapseRepeatedWordSequences(String[] parts) {
        final int minRepeats = 3;     // require ≥ 3 repeats
        final int maxPhraseLen = 8;   // typical heading phrases are short

        final int n = parts.length;
        if (n < minRepeats)
            return parts;

        for (int start = 0; start < n; start++) {
            for (int phraseLen = 1; phraseLen <= maxPhraseLen && start + phraseLen <= n; phraseLen++) {

                int count = 1;

                while (true) {
                    int nextStart = start + count * phraseLen;
                    if (nextStart + phraseLen > n)
                        break;

                    boolean equal = true;
                    for (int k = 0; k < phraseLen; k++) {
                        if (!parts[start + k].equals(parts[nextStart + k])) {
                            equal = false;
                            break;
                        }
                    }

                    if (!equal)
                        break;

                    count++;
                }

                if (count >= minRepeats) {
                    // collapse
                    int newSize = n - (count - 1) * phraseLen;
                    String[] result = new String[newSize];

                    int idx = 0;

                    // prefix
                    for (int i = 0; i < start; i++)
                        result[idx++] = parts[i];

                    // one copy of the repeated phrase
                    for (int k = 0; k < phraseLen; k++)
                        result[idx++] = parts[start + k];

                    // tail
                    int tailStart = start + count * phraseLen;
                    for (int i = tailStart; i < n; i++)
                        result[idx++] = parts[i];

                    return result;
                }
            }
        }

        return parts;
    }


    /**
     * Collapses repeated substring patterns inside a single token.
     * <p>
     * Only applies when:
     * - token length ≥ 4 (avoid collapsing "哈哈哈哈", etc.)
     * - base unit length between 4..10
     * - the token consists of N ≥ 3 consecutive repeats
     * <p>
     * Examples:
     * "abcdabcdabcd" → "abcd"
     * "第一季大结局第一季大结局第一季大结局" → "第一季大结局"
     */
    private static String collapseRepeatedToken(String token) {
        if (token == null)
            return null;

        int len = token.length();
        if (len < 4 || len > 200)
            return token;

        // Require at least 3 repeats (so unitLen <= len / 3)
        for (int unitLen = 4; unitLen <= 10 && unitLen <= len / 3; unitLen++) {

            if (len % unitLen != 0)
                continue;

            String unit = token.substring(0, unitLen);
            boolean allMatch = true;

            for (int pos = 0; pos < len; pos += unitLen) {
                if (!token.regionMatches(pos, unit, 0, unitLen)) {
                    allMatch = false;
                    break;
                }
            }

            if (allMatch) {
                return unit;
            }
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

    private static int indexOfAny(String text, char[] chars) {
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

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return (end == s.length()) ? s : s.substring(0, end);
    }

    private static int indexOfChar(char[] array, char ch) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAllAscii(String s) {
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) > 0x7F)
                return false;
        return true;
    }

    private static boolean isAllCjk(String s) {
        if (s == null || s.isEmpty())
            return false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // Treat any whitespace (including full-width space) as NOT CJK heading content
            if (Character.isWhitespace(ch))
                return false;

            if (!isCjk(ch))
                return false;
        }

        return true;
    }

    /**
     * Minimal CJK checker (BMP focused).
     * Designed for heading / structure heuristics, not full Unicode linguistics.
     */
    private static boolean isCjk(char ch) {

        // CJK Unified Ideographs Extension A (U+3400–U+4DBF)
        if ((int) ch >= 0x3400 && (int) ch <= 0x4DBF)
            return true;

        // CJK Unified Ideographs (U+4E00–U+9FFF)
        if ((int) ch >= 0x4E00 && (int) ch <= 0x9FFF)
            return true;

        // CJK Compatibility Ideographs (U+F900–U+FAFF)
        return (int) ch >= 0xF900 && (int) ch <= 0xFAFF;
    }

    private static boolean isAllCjkIgnoringWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            if (ch <= 0x7F) return false; // ASCII => not all-CJK
        }
        return true;
    }

}
