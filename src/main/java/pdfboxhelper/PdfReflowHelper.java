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

    static {
        for (char c : CJK_PUNCT_END_CHARS)
            CJK_PUNCT_END_TABLE[c] = true;
    }

    private static boolean isCjkPunctEnd(char ch) {
        return CJK_PUNCT_END_TABLE[ch];
    }

    /**
     * Chapter / heading detection
     */
    private static final Pattern TITLE_HEADING_REGEX = Pattern.compile(
            "(?x)^" +
                    "(?!.*[,，])" +
                    "(?=.{0,50}$)" +
                    "(" +
                    "前言|序章|楔子|终章|尾声|后记|尾聲|後記" +
                    "|番外.{0,15}" +
                    "|.{0,10}?第.{0,5}?([章节部卷節回][^分合的])" +
                    "|[卷章][一二三四五六七八九十](?:$|.{0,20}?)" +
                    ")"
    );

    /**
     * Lines with 2+ leading ASCII/full-width spaces are considered indented
     */
    private static final Pattern INDENT_REGEX = Pattern.compile("^[\\s\u3000]{2,}");

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

    private static boolean isDialogOpener(char ch) {
        return DIALOG_OPENERS.indexOf(ch) >= 0;
    }

    private static boolean isDialogCloser(char ch) {
        return DIALOG_CLOSERS.indexOf(ch) >= 0;
    }

    // ---------------------------------------------------------------------
    // Bracket punctuations (open → close)
    // ---------------------------------------------------------------------
    private static final Map<Character, Character> BRACKET_PAIRS;

    static {
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

        BRACKET_PAIRS = Collections.unmodifiableMap(map);
    }

    private static final Set<Character> OPEN_BRACKET_SET =
            Collections.unmodifiableSet(new HashSet<>(BRACKET_PAIRS.keySet()));

    private static final Set<Character> CLOSE_BRACKET_SET =
            Collections.unmodifiableSet(new HashSet<>(BRACKET_PAIRS.values()));

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    public static boolean isBracketOpener(char ch) {
        return OPEN_BRACKET_SET.contains(ch);
    }

    public static boolean isBracketCloser(char ch) {
        return CLOSE_BRACKET_SET.contains(ch);
    }

    public static boolean isMatchingBracket(char open, char close) {
        Character expected = BRACKET_PAIRS.get(open);
        return expected != null && expected == close;
    }

    // Metadata key-value separators
    private static final char[] METADATA_SEPARATORS = new char[]{
            '：', // full-width colon
            ':', // ASCII colon
            '　', // full-width ideographic space (U+3000)
            '·', // Middle dot (Latin)
            '・', // Katakana middle dot
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
                    "發行", "发行",
                    "掃描", "扫描",
                    "OCR",

                    // ===== 6. CIP / Cataloging =====
                    "CIP",
                    "在版編目", "在版编目",
                    "分類號", "分类号",
                    "主題詞", "主题词",
                    "類型", "类型",
                    "標簽", "标签",
                    "系列",

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
                    // Light rule: only flush on blank line if buffer ends with STRONG sentence end.
                    // Otherwise, treat as a soft cross-page blank line and keep accumulating.
                    int idx = findLastNonWhitespaceIndex(buffer);
                    if (idx >= 0 && !isStrongSentenceEnd(buffer.charAt(idx))) {
                        continue;
                    }
                }

                // End of paragraph → flush buffer (do NOT emit "")
                if (buffer.length() > 0) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }

                // IMPORTANT: Emitting empty segments would introduce hard paragraph boundaries
                // and break cross-line reflow
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
                            else splitAsHeading = !allCjk || isCjkPunctEnd(last);
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

            // Finalizer: strong sentence end → flush immediately. Do not remove.
            // If the current line completes a strong sentence, append it and flush immediately.
            if (buffer.length() > 0) {
                int idx = findLastNonWhitespaceIndex(stripped); // stripped is a String
                if (idx >= 0 && isStrongSentenceEnd(stripped.charAt(idx))) {
                    buffer.append(stripped);               // buffer now has new value
                    segments.add(buffer.toString());       // emit UPDATED buffer
                    buffer.setLength(0);                   // clear buffer
                    dialogState.reset();
                    dialogState.update(stripped);
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
            if (currentIsDialogStart) {

                boolean shouldFlushPrev = !bufferText.isEmpty();

                if (shouldFlushPrev) {
                    String trimmed = rtrim(bufferText);
                    char last = trimmed.isEmpty() ? '\0' : trimmed.charAt(trimmed.length() - 1);

                    shouldFlushPrev =
                            (last != '，' && last != ',' && last != '、') &&
                                    !dialogState.isUnclosed() &&
                                    !hasUnclosedBracket(bufferText);
                }

                if (shouldFlushPrev) {
                    segments.add(bufferText);
                    buffer.setLength(0);
                }

                // Start (or continue) the dialog paragraph
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- Colon + dialog continuation ---
            if (bufferText.endsWith("：") || bufferText.endsWith(":")) {
                if (isDialogOpener(stripped.charAt(0))) {
                    buffer.append(stripped);
                    dialogState.update(stripped);
                    continue;
                }
            }

            // 8a) Strong sentence boundary (handles 。！？, OCR . / :, “.”)
            if (!dialogState.isUnclosed() && endsWithSentenceBoundary(bufferText)) {
                segments.add(bufferText);         // push old buffer as a segment
                buffer.setLength(0);                  // take() semantics
                buffer.append(stripped);              // start new buffer with current line
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- CJK punctuation → paragraph end ---
            if (!bufferText.isEmpty()
                    && isCjkPunctEnd(buffer.charAt(buffer.length() - 1))
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
        return !s.isEmpty() && isDialogOpener(s.charAt(0));
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
        char last = s.charAt(len - 1);

        if (len > 2 && isMatchingBracket(s.charAt(0), last) && isMostlyCjk(s)) {
            return true;
        }

        int maxLen = isAllAscii(s) || isMixedCjkAscii(s) ? 16 : 8;

        // Short circuit for item title-like: "物品准备："
        if ((last == ':' || last == '：') && len <= maxLen && isAllCjkNoWhiteSpace(s.substring(0, len - 1))) {
            return true;
        }
        // If *ends* with CJK punctuation → not heading
        if (isCjkPunctEnd(last)) {
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

        for (int i = 0, n = s.length(); i < n; i++) {
            char ch = s.charAt(i);

            // If we see any closer, it's not "unclosed bracket" by your definition.
            if (isBracketCloser(ch)) {
                return false; // early exit
            }

            if (!hasOpen && isBracketOpener(ch)) {
                hasOpen = true;
                // We keep scanning only to see if a closer appears later.
            }
        }

        return hasOpen;
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

    private static boolean isAllCjkNoWhiteSpace(String s) {
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

    /**
     * Returns true if the string contains BOTH:
     * - CJK (as defined by isCjk(ch)), and
     * - ASCII all number (A-Z, a-z 0-9) OR full-width digits (０-９),
     * while rejecting any other characters except neutral ASCII separators:
     * space, '-', '/', ':', '.'
     */
    static boolean isMixedCjkAscii(String s) {
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
    private static boolean isMostlyCjk(String s) {
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

    private static int findLastNonWhitespaceIndex(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isStrongSentenceEnd(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?';
    }

    // ------ Sentence Boundary start ------ //

    private static boolean endsWithSentenceBoundary(String s) {
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
        if (isStrongSentenceEnd(last)) {
            return true;
        }

        // 2) Level-2 accepts OCR '.' / ':' at line end (mostly-CJK).
        if ((last == '.' || last == ':') && isOcrCjkAsciiPunctAtLineEnd(s, lastNonWs)) {
            return true;
        }

        // 3) Quote closers after strong end, plus OCR artifact `.“”` / `.」` / `.）`.
        if (isQuoteCloser(last)) {
            int prevNonWs = findPrevNonWhitespaceCharIndex(s, lastNonWs);
            if (prevNonWs >= 0) {
                char prev = s.charAt(prevNonWs);

                // Strong end immediately before quote closer.
                if (isStrongSentenceEnd(prev)) {
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

        // 4) Bracket closers with mostly CJK.
        if (isBracketCloser(last) && lastNonWs > 0 && isMostlyCjk(s)) {
            return true;
        }

        // 5) Ellipsis as weak boundary.
        return endsWithEllipsis(s);
    }

    private static boolean isQuoteCloser(char ch) {
        // Rust: is_dialog_closer(ch)
        return isDialogCloser(ch);
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
            if (isQuoteCloser(ch) || isBracketCloser(ch)) continue;
            return false;
        }
        return true;
    }

    private static boolean endsWithEllipsis(String s) {
        String t = trimEnd(s);
        return t.endsWith("…") || t.endsWith("……") || t.endsWith("...") || t.endsWith("..");
    }

}
