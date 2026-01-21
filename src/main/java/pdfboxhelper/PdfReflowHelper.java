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
        final PunctSets.CharRef lastRef = new PunctSets.CharRef();
        final PunctSets.CharRef prevRef = new PunctSets.CharRef();
        final PunctSets.IndexCharRef lastIdxRef = new PunctSets.IndexCharRef();

        for (String rawLine : lines) {

            // 1) Visual form: trim right, remove half-width indent
            String stripped = CjkText.trimEnd(rawLine);
            stripped = stripHalfWidthIndentKeepFullWidth(stripped);

            // 2) Probe form (for structural / heading detection): remove all indentation
            String probe = trimStartSpacesAndFullWidth(stripped);

            // 🧱 ABSOLUTE STRUCTURAL RULE — must be first (run on probe, output stripped)
            if (PunctSets.isVisualDividerLine(probe)) {
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

            // We already have some text in buffer
            String bufferText = buffer.length() > 0 ? buffer.toString() : "";
            boolean hasUnclosedBracket = buffer.length() > 0 && PunctSets.hasUnclosedBracket(bufferText);

            // --- Empty line ---
            if (stripped.isEmpty()) {
                if (!addPdfPageHeader && buffer.length() > 0) {

                    // NEW: If dialog/bracket is unclosed, blank line is soft (cross-page artifact).
                    if (dialogState.isUnclosed() || hasUnclosedBracket)
                        continue;

                    // Light rule: only flush on blank line if buffer ends with STRONG sentence end.
                    // Otherwise, treat as soft cross-page blank line.
                    if (!PunctSets.tryGetLastNonWhitespace(bufferText, lastIdxRef))
                        continue; // buffer is whitespace-only -> keep accumulating

                    if (!PunctSets.isStrongSentenceEnd(lastIdxRef.ch))
                        continue;
                }

                // End of paragraph → flush buffer (do NOT emit "")
                if (buffer.length() > 0) {
                    segments.add(bufferText);
                    buffer.setLength(0);
                    dialogState.reset();
                }

                continue;
            }

            // --- Page markers ---
            if (stripped.startsWith("=== ") && stripped.endsWith("===")) {
                if (buffer.length() > 0) {
                    segments.add(bufferText);
                    buffer.setLength(0);
                    dialogState.reset();
                }
                segments.add(stripped);
                continue;
            }

            // --- Titles (force flushing) ---
            if (isTitleHeading) {
                if (buffer.length() > 0) {
                    segments.add(bufferText);
                    buffer.setLength(0);
                    dialogState.reset();
                }
                segments.add(stripped);
                continue;
            }

            // 3b) Metadata
            if (isMetadata) {
                if (buffer.length() > 0) {
                    segments.add(bufferText);
                    buffer.setLength(0);
                    dialogState.reset();
                }

                // Metadata 每行獨立存放（之後你可以決定係 skip、折疊、顯示）
                segments.add(stripped);
                continue;
            }

            // 3c) Weak heading-like: only active when previous paragraph is "safe" AND looks ended.
            if (isShortHeading) {

                final boolean allCjk = CjkText.isAllCjkIgnoringWhitespace(stripped);

                // Decide if current short line should become a standalone heading (and cause a split)
                boolean splitAsHeading;

                if (buffer.length() == 0) {
                    // file start / just flushed -> allow heading alone
                    splitAsHeading = true;
                } else {
                    if (hasUnclosedBracket) {
                        // previous paragraph is "unsafe" -> must treat as continuation
                        splitAsHeading = false;
                    } else {
                        // Find last non-whitespace char of previous bufferText (no rtrim allocation)
                        if (!PunctSets.tryGetLastNonWhitespace(bufferText, lastRef)) {
                            // buffer has only whitespace -> treat like no previous paragraph
                            splitAsHeading = true;
                        } else {
                            final char last = lastRef.value;

                            boolean prevEndsWithCommaLike = PunctSets.isCommaLike(last);
                            boolean prevEndsWithSentencePunct = PunctSets.isClauseOrEndPunct(last);

                            boolean currentLooksLikeContinuationMarker =
                                    allCjk
                                            || PunctSets.endsWithColonLike(stripped)
                                            || PunctSets.endsWithAllowedPostfixCloser(stripped);
                            // previous ends with comma -> continuation
                            if (prevEndsWithCommaLike) {
                                splitAsHeading = false;
                            }
                            // all-CJK short heading line + previous not ended by sentence punctuation -> continuation
                            else splitAsHeading = !currentLooksLikeContinuationMarker || prevEndsWithSentencePunct;
                        }
                    }
                }

                if (splitAsHeading) {
                    if (buffer.length() > 0) {
                        segments.add(bufferText);
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
            if (buffer.length() > 0 && !dialogState.isUnclosed() && !hasUnclosedBracket) {
                if (PunctSets.tryGetLastNonWhitespace(stripped, lastRef)
                        && PunctSets.isStrongSentenceEnd(lastRef.value)) {
                    buffer.append(stripped);          // buffer now has new value
                    segments.add(buffer.toString());  // emit UPDATED bufferText, not old bufferText
                    buffer.setLength(0);              // clear buffer
                    dialogState.reset();
                    dialogState.update(stripped);
                    continue;
                }
            }

            // ------ Buffer first line ------
            if (buffer.length() == 0) {
                // Start new paragraph
                buffer.append(stripped);
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // Check dialog start
            boolean currentIsDialogStart = PunctSets.isDialogStarter(stripped);

            // 🔸 NEW RULE: If previous line ends with comma,
            //     do NOT flush even if this line starts dialog.
            //     (comma-ending means the sentence is not finished)
            if (currentIsDialogStart) {

                boolean shouldFlushPrev = !bufferText.isEmpty();

                if (shouldFlushPrev) {
                    if (dialogState.isUnclosed() || hasUnclosedBracket) {
                        shouldFlushPrev = false;
                    } else if (!PunctSets.tryGetLastNonWhitespace(bufferText, lastRef)) {
                        // whitespace-only buffer → treat as empty
                        shouldFlushPrev = false;
                    } else {
                        char last = lastRef.value;

                        // 1) comma-like → continuation
                        if (PunctSets.isCommaLike(last)) {
                            shouldFlushPrev = false;
                        }
                        // 2) ends with CJK ideograph (NO punctuation at all) → continuation
                        else if (CjkText.isCjk(last)) {
                            shouldFlushPrev = false;
                        }
                        // else: punctuation or ASCII letter/digit → allow flush
                    }
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

            // 🔸 9b) Dialog end line: ends with dialog closer.
            // Flush when the char before closer is strong end,
            // and bracket safety is satisfied (with a narrow typo override).
            if (PunctSets.tryGetLastNonWhitespace(stripped, lastIdxRef) &&
                    PunctSets.isDialogCloser(lastIdxRef.ch)) {
                // Check punctuation right before the closer (e.g., “？” / “。”)
                boolean punctBeforeCloserIsStrong =
                        PunctSets.tryGetPrevNonWhitespace(stripped, lastIdxRef.index, prevRef) &&
                                PunctSets.isClauseOrEndPunct(prevRef.value);

                // Snapshot bracket safety BEFORE appending current line
                boolean lineHasBracketIssue = PunctSets.hasUnclosedBracket(stripped);

                buffer.append(stripped);
                dialogState.update(stripped);

                // Allow flush if:
                // - dialog is closed after this line
                // - punctuation before closer is a strong end
                // - and either:
                //     (a) buffer has no bracket issue, OR
                //     (b) buffer has bracket issue but this line itself is the culprit (OCR/typo),
                //        so allow a dialog-end flush anyway.
                if (!dialogState.isUnclosed() &&
                        punctBeforeCloserIsStrong &&
                        (!hasUnclosedBracket || lineHasBracketIssue)) {
                    segments.add(buffer.toString());
                    buffer.setLength(0);
                    dialogState.reset();
                }

                continue;
            }

            // --- Colon + dialog continuation ---
//            if (bufferText.endsWith("：") || bufferText.endsWith(":")) {
//                if (PunctSets.isDialogOpener(stripped.charAt(0))) {
//                    buffer.append(stripped);
//                    dialogState.update(stripped);
//                    continue;
//                }
//            }

            // 8a) Strong sentence boundary (handles 。！？, OCR . / :, “.”)
            boolean flushOnSentenceEnd =
                    !dialogState.isUnclosed()
                            && !hasUnclosedBracket
                            && CjkText.endsWithSentenceBoundary(bufferText);

            // 8b) Closing CJK bracket boundary → new paragraph
            // Handles cases where a paragraph ends with a full-width closing bracket/quote
            // (e.g. ）】》」) and should not be merged with the next line.
            boolean flushOnBracketBoundary =
                    !dialogState.isUnclosed()
                            && CjkText.endsWithCjkBracketBoundary(bufferText);

            if (flushOnSentenceEnd || flushOnBracketBoundary) {
                segments.add(bufferText);  // push old buffer as a segment
                buffer.setLength(0);       // take() semantics
                buffer.append(stripped);   // start new buffer with current line
                dialogState.reset();
                dialogState.update(stripped);
                continue;
            }

            // --- CJK punctuation → paragraph end ---
//            if (!bufferText.isEmpty()
//                    && isCjkPunctEnd(buffer.charAt(buffer.length() - 1))
//                    && !hasUnclosedBracket
//                    && !dialogState.isUnclosed()) {
//
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
    @SuppressWarnings("unused")
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

    private static boolean isHeadingLike(String s) {
        if (s == null) return false;

        s = s.trim();
        if (s.isEmpty()) return false;

        // keep page markers intact
        if (s.startsWith("=== ") && s.endsWith("===")) {
            return false;
        }

        // Reject headings with unclosed brackets
        if (PunctSets.hasUnclosedBracket(s)) {
            return false;
        }

        int len = s.length();
        char last = s.charAt(len - 1);

        if (len > 2 && PunctSets.isMatchingBracket(s.charAt(0), last) && CjkText.isMostlyCjk(s)) {
            return true;
        }

        int maxLen = CjkText.isAllAscii(s) || CjkText.isMixedCjkAscii(s) ? 16 : 8;

        // Short circuit for item title-like: "物品准备："
        if (PunctSets.isColonLike(last) && len <= maxLen && CjkText.isAllCjkNoWhiteSpace(s.substring(0, len - 1))) {
            return true;
        }

        if (PunctSets.isAllowedPostfixCloser(last) && !PunctSets.containsAnyCommaLike(s)) {
            return true;
        }

        // If *ends* with CJK punctuation → not heading
        if (PunctSets.isClauseOrEndPunct(last)) {
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
            if (hasNonAscii && !PunctSets.isCommaLike(last)) {
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
        int idx = CjkText.indexOfAny(line, PunctSets.METADATA_SEPARATORS);
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
        return !PunctSets.isDialogOpener(line.charAt(j));
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

}
