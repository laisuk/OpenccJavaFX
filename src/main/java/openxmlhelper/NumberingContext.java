package openxmlhelper;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// -----------------------------------------------------------------------------
// Numbering support (DOCX lists: 1., 1.1, •, etc.) — Java 8 port of your C# logic
// -----------------------------------------------------------------------------

/**
 * DOCX list numbering resolver.
 *
 * <p>Currently optional and safe to ignore.
 * When not used, it produces no prefixes and does not affect text extraction.
 *
 * <p>Designed for future fidelity improvements (1., 1.1, •, etc.)
 * without impacting OpenCC conversion workflows.
 */
final class NumberingContext {

    // numId -> abstractNumId
    private final HashMap<Integer, Integer> numToAbstract = new HashMap<Integer, Integer>();

    // abstractNumId -> (ilvl -> LevelDef)
    private final HashMap<Integer, HashMap<Integer, LevelDef>> abstractLevels =
            new HashMap<Integer, HashMap<Integer, LevelDef>>();

    // styleId -> (numId, ilvl)
    private final HashMap<String, StyleNum> styleNum = new HashMap<String, StyleNum>();

    // numId -> counters[0..8]
    private final HashMap<Integer, int[]> counters = new HashMap<Integer, int[]>();

    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern LVL_TEXT_TOKEN = Pattern.compile("%([1-9])");

    void resetCountersForPart() {
        counters.clear();
    }

    ResolvedNum resolveNum(Integer directNumId, Integer directIlvl, String styleId) {
        if (directNumId != null && directIlvl != null) {
            return new ResolvedNum(directNumId, directIlvl);
        }
        if (styleId != null && !styleId.isEmpty()) {
            StyleNum s = styleNum.get(styleId);
            if (s != null) {
                return new ResolvedNum(s.numId, s.ilvl);
            }
        }
        return new ResolvedNum(null, null);
    }

    String nextPrefix(int numId, int ilvl) {
        // clamp ilvl to 0..8
        if (ilvl < 0) ilvl = 0;
        if (ilvl > 8) ilvl = 8;

        Integer absId = numToAbstract.get(numId);
        if (absId == null) return "";

        HashMap<Integer, LevelDef> lvls = abstractLevels.get(absId);
        if (lvls == null) return "";

        LevelDef def = lvls.get(ilvl);
        if (def == null) return "";

        int[] arr = counters.computeIfAbsent(numId, k -> new int[9]);

        // increment this level, reset deeper levels
        arr[ilvl]++;
        for (int d = ilvl + 1; d < arr.length; d++) arr[d] = 0;

        // bullets
        if (Utils.equalsIgnoreCase(def.numFmt, "bullet")) {
            return "• ";
        }

        String lvlText = (def.lvlText == null || def.lvlText.isEmpty()) ? "%1." : def.lvlText;

        // Replace %1..%9
        Matcher m = LVL_TEXT_TOKEN.matcher(lvlText);
        StringBuffer sb = new StringBuffer(lvlText.length() + 8);
        while (m.find()) {
            int k = (m.group(1).charAt(0) - '1'); // 0..8
            int v = arr[k];
            if (v <= 0) v = 1;
            m.appendReplacement(sb, Matcher.quoteReplacement(Integer.toString(v)));
        }
        m.appendTail(sb);

        String prefix = sb.toString()
                .replace("\t", " ")
                .replace("\u00A0", " "); // nbsp -> normal space

        if (!prefix.isEmpty() && !Character.isWhitespace(prefix.charAt(prefix.length() - 1))) {
            prefix += " ";
        }
        return prefix;
    }

    static NumberingContext load(ZipFile zip) throws IOException, XMLStreamException {
        NumberingContext ctx = new NumberingContext();
        ctx.loadNumbering(zip);
        ctx.loadStyles(zip);
        return ctx;
    }

    // ------------------------------ numbering.xml ------------------------------

    private void loadNumbering(ZipFile zip) throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("word/numbering.xml");
        if (entry == null) return;

        try (InputStream stream = zip.getInputStream(entry)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            Utils.trySet(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
            Utils.trySet(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

            XMLStreamReader r = factory.createXMLStreamReader(stream, StandardCharsets.UTF_8.name());

            Integer currentAbstractId = null;
            Integer currentLevel = null;

            try {
                while (r.hasNext()) {
                    int ev = r.next();

                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        if (!NS_W.equals(r.getNamespaceURI())) continue;

                        String local = r.getLocalName();

                        if ("num".equals(local)) {
                            Integer numId = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "numId"));
                            if (numId != null) {
                                // Read inside <w:num> ... </w:num> to find <w:abstractNumId w:val="X"/>
                                int depth = 1;
                                while (depth > 0 && r.hasNext()) {
                                    int ev2 = r.next();
                                    if (ev2 == XMLStreamConstants.START_ELEMENT) {
                                        if (NS_W.equals(r.getNamespaceURI()) && "abstractNumId".equals(r.getLocalName())) {
                                            Integer absId = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "val"));
                                            if (absId != null) {
                                                numToAbstract.put(numId, absId);
                                            }
                                        }
                                        depth++;
                                    } else if (ev2 == XMLStreamConstants.END_ELEMENT) {
                                        depth--;
                                    }
                                }
                            }
                            continue;
                        }

                        if ("abstractNum".equals(local)) {
                            Integer absId = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "abstractNumId"));
                            if (absId != null) {
                                currentAbstractId = absId;
                                if (!abstractLevels.containsKey(absId)) {
                                    abstractLevels.put(absId, new HashMap<>());
                                }
                            }
                            continue;
                        }

                        if ("lvl".equals(local)) {
                            if (currentAbstractId != null) {
                                Integer ilvl = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "ilvl"));
                                if (ilvl != null) {
                                    currentLevel = ilvl;
                                    HashMap<Integer, LevelDef> map = abstractLevels.get(currentAbstractId);
                                    if (map != null && !map.containsKey(ilvl)) {
                                        map.put(ilvl, new LevelDef());
                                    }
                                }
                            }
                            continue;
                        }

                        if ("numFmt".equals(local)) {
                            if (currentAbstractId != null && currentLevel != null) {
                                String val = Utils.getAttrAny(r, NS_W, "val");
                                if (val == null) val = "";
                                HashMap<Integer, LevelDef> map = abstractLevels.get(currentAbstractId);
                                if (map != null) map.get(currentLevel).numFmt = val;
                            }
                            continue;
                        }

                        if ("lvlText".equals(local)) {
                            if (currentAbstractId != null && currentLevel != null) {
                                String val = Utils.getAttrAny(r, NS_W, "val");
                                if (val == null) val = "";
                                HashMap<Integer, LevelDef> map = abstractLevels.get(currentAbstractId);
                                if (map != null) map.get(currentLevel).lvlText = val;
                            }
                        }
                    } else if (ev == XMLStreamConstants.END_ELEMENT) {
                        if (!NS_W.equals(r.getNamespaceURI())) continue;

                        String local = r.getLocalName();
                        if ("abstractNum".equals(local)) {
                            currentAbstractId = null;
                            currentLevel = null;
                        } else if ("lvl".equals(local)) {
                            currentLevel = null;
                        }
                    }
                }
            } finally {
                try {
                    r.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    // ------------------------------- styles.xml -------------------------------

    private void loadStyles(ZipFile zip) throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("word/styles.xml");
        if (entry == null) return;

        try (InputStream stream = zip.getInputStream(entry)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            Utils.trySet(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
            Utils.trySet(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

            XMLStreamReader r = factory.createXMLStreamReader(stream, StandardCharsets.UTF_8.name());

            String currentStyleId = null;
            Integer styleNumId = null;
            Integer styleIlvl = null;

            try {
                while (r.hasNext()) {
                    int ev = r.next();

                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        if (!NS_W.equals(r.getNamespaceURI())) continue;

                        String local = r.getLocalName();

                        if ("style".equals(local)) {
                            currentStyleId = Utils.getAttrAny(r, NS_W, "styleId");
                            styleNumId = null;
                            styleIlvl = null;
                            continue;
                        }

                        if ("numId".equals(local)) {
                            if (currentStyleId != null) {
                                styleNumId = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "val"));
                            }
                            continue;
                        }

                        if ("ilvl".equals(local)) {
                            if (currentStyleId != null) {
                                styleIlvl = Utils.tryParseInt(Utils.getAttrAny(r, NS_W, "val"));
                            }
                        }
                    } else if (ev == XMLStreamConstants.END_ELEMENT) {
                        if (!NS_W.equals(r.getNamespaceURI())) continue;

                        if ("style".equals(r.getLocalName())) {
                            if (currentStyleId != null && !currentStyleId.isEmpty()
                                    && styleNumId != null && styleIlvl != null) {
                                styleNum.put(currentStyleId, new StyleNum(styleNumId, styleIlvl));
                            }
                            currentStyleId = null;
                            styleNumId = null;
                            styleIlvl = null;
                        }
                    }
                }
            } finally {
                try {
                    r.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    static final class ResolvedNum {
        final Integer numId;
        final Integer ilvl;

        ResolvedNum(Integer numId, Integer ilvl) {
            this.numId = numId;
            this.ilvl = ilvl;
        }

        boolean isValid() {
            return numId != null && ilvl != null;
        }
    }

    // ------------------------------- helpers --------------------------------

    // ------------------------------- models ---------------------------------

    private static final class StyleNum {
        final int numId;
        final int ilvl;

        StyleNum(int numId, int ilvl) {
            this.numId = numId;
            this.ilvl = ilvl;
        }
    }

    private static final class LevelDef {
        String numFmt = "";
        String lvlText = "";
    }
}
