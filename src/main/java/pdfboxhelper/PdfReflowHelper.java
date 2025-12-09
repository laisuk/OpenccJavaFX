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
            '）', '】', '》', '〗', '〔', '〉', '」', '』', '］', '｝', ':', ')',
    };

    /**
     * Chapter / heading detection
     */
    private static final Pattern TITLE_HEADING_REGEX = Pattern.compile(
            "(?x)^ (?=.{0,60}$)(前言|序章|终章|尾声|后记|番外|尾聲|後記|.{0,20}?第.{0,10}?([章节部卷節回][^分合]).{0,20}?)"
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
    private static final String OPEN_BRACKETS = "（([【《";
    private static final String CLOSE_BRACKETS = "）)]】》";

    // Metadata key-value separators
    private static final char[] METADATA_SEPARATORS = new char[]{
            '：', // full-width colon
            ':', // ASCII colon
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

            // 2) Logical form for heading detection
            String headingProbe = trimStartSpacesAndFullWidth(stripped);
            boolean isTitleHeading = TITLE_HEADING_REGEX.matcher(headingProbe).find();
            boolean isShortHeading = isHeadingLike(stripped);
            boolean isMetadata = isMetadataLine(stripped);


            // Style-layer repeated titles
            if (isTitleHeading) {
                stripped = collapseRepeatedSegments(stripped);
            }

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

            // 3c) 弱 heading-like：只在「上一段安全」且「上一段尾部像一句話的結束」時才生效
            if (isShortHeading) {

                // 判斷當前行是否「全 CJK」（忽略空白）
                boolean isAllCjk = true;
                for (int i = 0; i < stripped.length(); i++) {
                    char ch = stripped.charAt(i);
                    if (Character.isWhitespace(ch)) {
                        continue;
                    }
                    if (ch <= 0x7F) {
                        isAllCjk = false;
                        break;
                    }
                }

                if (buffer.length() > 0) {
                    String bufText = buffer.toString();

                    // 🔐 1) 若上一段仍有未配對括號／書名號 → 必定是續行，不能當 heading
                    if (hasUnclosedBracket(bufText)) {
                        // fall through → 當普通行，由後面的 merge 邏輯處理
                    } else {
                        String bt = rtrim(bufText);
                        if (!bt.isEmpty()) {
                            char last = bt.charAt(bt.length() - 1);

                            // 🔸 2) 上一行逗號結尾 → 視作續句，不當 heading
                            if (last == '，' || last == ',') {
                                // fall through → default merge
                            }
                            // 🔸 3) 對於「全 CJK 的短 heading-like」，
                            //     如果上一行 *不是* 以 CJK 句末符號結束，也當續句，不切段。
                            else if (isAllCjk && indexOfChar(CJK_PUNCT_END_CHARS, last) < 0) {
                                // e.g.:
                                //   内容简介： 《盗
                                //   墓笔记:吴邪的盗墓笔   ← 雖然像短 heading，但上一行未「句號收尾」
                                // fall through → 當續行
                            } else {
                                // ✅ 真 heading-like → flush 舊段，再把當前行當作獨立 heading
                                segments.add(bufText);
                                buffer.setLength(0);
                                dialogState.reset();
                                segments.add(stripped);
                                continue;
                            }
                        } else {
                            // buffer 有長度但全空白，其實等同無 → 直接當 heading
                            segments.add(stripped);
                            continue;
                        }
                    }
                } else {
                    // buffer 空（文件開頭／上一段剛 flush 完）→ 允許短 heading 單獨出現
                    segments.add(stripped);
                    continue;
                }
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
            if (!bufferText.isEmpty()) {
                String trimmed = rtrim(bufferText);
                char last = trimmed.isEmpty() ? '\0' : trimmed.charAt(trimmed.length() - 1);

                if (last == '，' || last == ',') {
                    // fall through → treat as continuation
                    // do NOT flush here, even if currentIsDialogStart == true
                } else if (currentIsDialogStart) {
                    // *** DIALOG: if this line starts a dialog,
                    //     flush previous paragraph (only if safe)
                    segments.add(bufferText);
                    buffer.setLength(0);
                    buffer.append(stripped);
                    dialogState.reset();
                    dialogState.update(stripped);
                    continue;
                }
            } else {
                // buffer empty, just add new dialog line
                if (currentIsDialogStart) {
                    buffer.append(stripped);
                    dialogState.reset();
                    dialogState.update(stripped);
                    continue;
                }
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
        if (indexOfChar(CJK_PUNCT_END_CHARS, last) >= 0) { // uses CJK_PUNCT_END_CHARS
            return false;
        }

        // Reject headings with unclosed brackets
        if (hasUnclosedBracket(s)) {
            return false;
        }

        int len = s.length();

        // Short line heuristics (<= 15 chars)
        if (len <= 10) {

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

}
