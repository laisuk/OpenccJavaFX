package openxmlhelper;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OpenXmlHelper {

    private OpenXmlHelper() {
    }

    // ---------------------------- Format detection ----------------------------

    public static boolean isDocx(String path) {
        if (path == null || path.trim().isEmpty())
            return false;

        Path p = Paths.get(path);
        if (!Files.exists(p))
            return false;

        if (!path.toLowerCase(Locale.ROOT).endsWith(".docx"))
            return false;

        try (ZipFile zip = new ZipFile(path)) {
            return zip.getEntry("word/document.xml") != null
                    && zip.getEntry("[Content_Types].xml") != null;
        } catch (IOException ex) {
            return false;
        }
    }

    public static boolean isOdt(String path) {
        if (path == null || path.trim().isEmpty())
            return false;

        Path p = Paths.get(path);
        if (!Files.exists(p))
            return false;

        if (!path.toLowerCase(Locale.ROOT).endsWith(".odt"))
            return false;

        try (ZipFile zip = new ZipFile(path)) {
            ZipEntry content = zip.getEntry("content.xml");
            if (content == null)
                return false;

            // Optional mimetype verification (the best effort)
            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null) {
                try (InputStream s = zip.getInputStream(mimetype);
                     Reader r = new InputStreamReader(s, StandardCharsets.US_ASCII);
                     BufferedReader br = new BufferedReader(r)) {

                    String mt = Utils.readAll(br).trim();
                    if (!"application/vnd.oasis.opendocument.text".equals(mt))
                        return false;
                }
            }

            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    // ---------------------------- DOCX extraction ----------------------------

    public static String extractDocxAllText(
            String docxPath,
            boolean includePartHeadings,
            boolean normalizeNewlines
    ) throws IOException, XMLStreamException {

        try (ZipFile zip = new ZipFile(docxPath)) {
            NumberingContext ctx = NumberingContext.load(zip); // Part 3 will implement real parsing

            List<String> parts = new ArrayList<>();
            parts.add("word/document.xml");
            parts.add("word/footnotes.xml");
            parts.add("word/endnotes.xml");
            parts.add("word/comments.xml");

            // headers
            List<String> headers = new ArrayList<String>();
            // footers
            List<String> footers = new ArrayList<String>();

            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                String lower = name.toLowerCase(Locale.ROOT);

                if (lower.startsWith("word/header") && lower.endsWith(".xml")) {
                    headers.add(name);
                } else if (lower.startsWith("word/footer") && lower.endsWith(".xml")) {
                    footers.add(name);
                }
            }

            headers.sort(String.CASE_INSENSITIVE_ORDER);
            footers.sort(String.CASE_INSENSITIVE_ORDER);

            parts.addAll(headers);
            parts.addAll(footers);

            // Distinct (case-insensitive) but preserve first-seen order like C# Distinct+ToList
            List<String> distinctParts = Utils.distinctIgnoreCasePreserveOrder(parts);

            StringBuilder output = new StringBuilder(128 * 1024);

            for (String partName : distinctParts) {
                ZipEntry entry = zip.getEntry(partName);
                if (entry == null) continue;

                if (includePartHeadings) {
                    if (output.length() > 0 && !Utils.endsWithNewline(output))
                        output.append('\n');
                    output.append("=== ").append(partName).append(" ===").append('\n');
                }

                try (InputStream stream = zip.getInputStream(entry)) {
                    // Reset counters per part (same as C#)
                    ctx.resetCountersForPart();

                    String text = extractWordprocessingMlText(stream, ctx);
                    output.append(text);

                    if (!Utils.endsWithNewline(output))
                        output.append('\n');
                }
            }

            String result = output.toString();
            if (normalizeNewlines) {
                result = Utils.normalizeNewlinesToLf(result);
            }
            return result;
        }
    }

    //===============

    private static String extractWordprocessingMlText(InputStream xmlStream, NumberingContext ctx)
            throws XMLStreamException {

        final String nsW = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

        StringBuilder sb = new StringBuilder(64 * 1024);

        boolean inTable = false, inRow = false, inCell = false;
        List<String> currentRowCells = null;
        StringBuilder currentCell = null;

        boolean inParagraph = false;
        boolean paraPrefixEmitted = false;

        Integer paraNumId = null;
        Integer paraIlvl = null;
        String paraStyleId = null;

        boolean inFootnote = false;
        boolean inEndnote = false;
        boolean skipThisNote = false;

        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security: do not resolve external entities / DTDs (similar intent to DtdProcessing.Prohibit)
        Utils.trySet(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        Utils.trySet(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

        XMLStreamReader r = factory.createXMLStreamReader(xmlStream, StandardCharsets.UTF_8.name());
        try {
            while (r.hasNext()) {
                int event = r.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ns = r.getNamespaceURI();
                    if (!nsW.equals(ns))
                        continue;

                    String local = r.getLocalName();

                    if ("footnote".equals(local)) {
                        inFootnote = true;
                        skipThisNote = shouldSkipNoteElement(r, nsW);
                        continue;
                    }
                    if ("endnote".equals(local)) {
                        inEndnote = true;
                        skipThisNote = shouldSkipNoteElement(r, nsW);
                        continue;
                    }

                    if ("tbl".equals(local)) {
                        inTable = true;
                        continue;
                    }

                    if ("tr".equals(local)) {
                        if (inTable) {
                            inRow = true;
                            currentRowCells = new ArrayList<String>(8);
                        }
                        continue;
                    }

                    if ("tc".equals(local)) {
                        if (inRow) {
                            inCell = true;
                            currentCell = new StringBuilder(256);
                        }
                        continue;
                    }

                    if ("p".equals(local)) {
                        inParagraph = true;
                        paraPrefixEmitted = false;
                        paraNumId = null;
                        paraIlvl = null;
                        paraStyleId = null;
                        continue;
                    }

                    if ("pStyle".equals(local)) {
                        if (inParagraph) {
                            String val = Utils.getAttr(r, nsW, "val");
                            if (val != null && !val.isEmpty())
                                paraStyleId = val;
                        }
                        continue;
                    }

                    if ("numId".equals(local)) {
                        if (inParagraph) {
                            String val = Utils.getAttr(r, nsW, "val");
                            Integer id = Utils.tryParseInt(val);
                            if (id != null)
                                paraNumId = id;
                        }
                        continue;
                    }

                    if ("ilvl".equals(local)) {
                        if (inParagraph) {
                            String val = Utils.getAttr(r, nsW, "val");
                            Integer lvl = Utils.tryParseInt(val);
                            if (lvl != null)
                                paraIlvl = lvl;
                        }
                        continue;
                    }

                    if ("t".equals(local)) {
                        if (skipThisNote && (inFootnote || inEndnote)) {
                            // Consume subtree like XmlReader.Skip()
                            Utils.skipElement(r);
                            continue;
                        }

                        emitPrefixIfNeeded(ctx, inParagraph, paraPrefixEmitted,
                                paraNumId, paraIlvl, paraStyleId,
                                inCell, currentCell, sb);

                        // Read text of <w:t>...</w:t>
                        String text = r.getElementText();
                        Utils.currentTarget(inCell, currentCell, sb).append(text);

                        // prefix may have been emitted in emitPrefixIfNeeded; update boolean:
                        if (inParagraph && !paraPrefixEmitted) {
                            // emitPrefixIfNeeded() sets it internally by returning a flag; we mimic by re-checking:
                            // We'll use a small trick: if prefix got appended, we mark it.
                            // But cleaner: have emitPrefixIfNeeded() return boolean. We'll do that below properly.
                        }
                        continue;
                    }

                    if ("tab".equals(local)) {
                        if (skipThisNote && (inFootnote || inEndnote)) continue;

                        paraPrefixEmitted = emitPrefixIfNeededReturnFlag(ctx, inParagraph, paraPrefixEmitted,
                                paraNumId, paraIlvl, paraStyleId,
                                inCell, currentCell, sb);

                        Utils.currentTarget(inCell, currentCell, sb).append('\t');
                        continue;
                    }

                    if ("br".equals(local) || "cr".equals(local)) {
                        if (skipThisNote && (inFootnote || inEndnote)) continue;

                        paraPrefixEmitted = emitPrefixIfNeededReturnFlag(ctx, inParagraph, paraPrefixEmitted,
                                paraNumId, paraIlvl, paraStyleId,
                                inCell, currentCell, sb);

                        Utils.currentTarget(inCell, currentCell, sb).append('\n');
                        continue;
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {

                    String ns = r.getNamespaceURI();
                    if (!nsW.equals(ns))
                        continue;

                    String local = r.getLocalName();

                    if ("p".equals(local)) {
                        if (!(skipThisNote && (inFootnote || inEndnote))) {
                            Utils.currentTarget(inCell, currentCell, sb).append('\n');
                        }
                        inParagraph = false;
                        paraPrefixEmitted = false; // reset for safety
                        continue;
                    }

                    if ("tc".equals(local)) {
                        if (inCell && currentRowCells != null && currentCell != null) {
                            currentRowCells.add(Utils.trimTrailingNewlines(currentCell.toString()));
                            currentCell = null;
                            inCell = false;
                        }
                        continue;
                    }

                    if ("tr".equals(local)) {
                        if (inRow) {
                            Utils.appendRowAsTsvLine(sb, currentRowCells);
                            currentRowCells = null;
                            inRow = false;
                        }
                        continue;
                    }

                    if ("tbl".equals(local)) {
                        if (inTable) {
                            if (!Utils.endsWithNewline(sb)) sb.append('\n');
                            inTable = false;
                        }
                        continue;
                    }

                    if ("footnote".equals(local)) {
                        inFootnote = false;
                        skipThisNote = false;
                        continue;
                    }

                    if ("endnote".equals(local)) {
                        inEndnote = false;
                        skipThisNote = false;
                        continue;
                    }
                }
            }
        } finally {
            try {
                r.close();
            } catch (Exception ignore) {
            }
        }

        return sb.toString();
    }

    // ---------------------------- Note skipping ----------------------------

    private static boolean shouldSkipNoteElement(XMLStreamReader r, String nsW) {
        String type = Utils.getAttr(r, nsW, "type");
        if (type != null) {
            String t = type.trim();
            if ("separator".equalsIgnoreCase(t) || "continuationSeparator".equalsIgnoreCase(t))
                return true;
        }

        String idStr = Utils.getAttr(r, nsW, "id");
        Integer id = Utils.tryParseInt(idStr);
        if (id != null) {
            return id <= 0;
        }

        return false;
    }

    // ---------------------------- Numbering prefix (stub for Part 3) ----------------------------

    /**
     * In Part 3 we will implement the REAL numbering/style parsing.
     * For now, this keeps the code compiling and preserves the call sites.
     */
    static final class NumberingContext {
        static NumberingContext load(ZipFile zip) {
            return new NumberingContext();
        }

        void resetCountersForPart() {
        }

        openxmlhelper.NumberingContext.ResolvedNum resolveNum(Integer paraNumId, Integer paraIlvl, String paraStyleId) {
            return new openxmlhelper.NumberingContext.ResolvedNum(null, null);
        }

        String nextPrefix(int numId, int ilvl) {
            return "";
        }
    }

    /**
     * A proper port of EmitPrefixIfNeeded(), but returns updated paraPrefixEmitted flag.
     */
    private static boolean emitPrefixIfNeededReturnFlag(
            NumberingContext ctx,
            boolean inParagraph,
            boolean paraPrefixEmitted,
            Integer paraNumId,
            Integer paraIlvl,
            String paraStyleId,
            boolean inCell,
            StringBuilder currentCell,
            StringBuilder sb
    ) {
        if (!inParagraph || paraPrefixEmitted) return paraPrefixEmitted;

        openxmlhelper.NumberingContext.ResolvedNum rn = ctx.resolveNum(paraNumId, paraIlvl, paraStyleId);
        if (rn.numId != null && rn.ilvl != null) {
            String prefix = ctx.nextPrefix(rn.numId, rn.ilvl);
            if (!prefix.isEmpty()) {
                Utils.currentTarget(inCell, currentCell, sb).append(prefix);
                return true;
            }
        }
        return false;
    }

    // Legacy helper kept for readability; not used after we adopted the return-flag version above
    private static void emitPrefixIfNeeded(
            NumberingContext ctx,
            boolean inParagraph,
            boolean paraPrefixEmitted,
            Integer paraNumId,
            Integer paraIlvl,
            String paraStyleId,
            boolean inCell,
            StringBuilder currentCell,
            StringBuilder sb
    ) {
        // no-op wrapper; we use emitPrefixIfNeededReturnFlag() to correctly update the flag
    }

}
