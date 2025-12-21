package openxmlhelper;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OpenDocumentHelper {

    private OpenDocumentHelper() {
    }

    // ODF namespaces (ODT)
    private static final String NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final String NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";

    /**
     * Extract all user-visible text from an ODT file (content.xml).
     * - Paragraphs/headings end with '\n'
     * - Tables rows are tab-separated, ending with '\n'
     */
    public static String extractOdtAllText(File odtFile) throws IOException, XMLStreamException {
        if (odtFile == null) throw new IllegalArgumentException("odtFile is null");

        try (ZipFile zip = new ZipFile(odtFile)) {
            ZipEntry content = zip.getEntry("content.xml");
            if (content == null) {
                throw new IOException("Invalid ODT: missing content.xml");
            }

            try (InputStream in = zip.getInputStream(content)) {
                return extractOdfContentXml(in);
            }
        }
    }

    private static String extractOdfContentXml(InputStream xmlStream) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security hardening
        trySet(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        trySet(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

        XMLStreamReader r = factory.createXMLStreamReader(xmlStream, StandardCharsets.UTF_8.name());

        StringBuilder out = new StringBuilder(64 * 1024);

        boolean inRow = false;
        boolean inCell = false;

        List<String> currentRowCells = null;
        StringBuilder currentCell = null;

        boolean inParagraphLike = false; // text:p or text:h (for newline on end)

        try {
            while (r.hasNext()) {
                int ev = r.next();

                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String ns = r.getNamespaceURI();
                    String local = r.getLocalName();

                    // ---- Tables ----
                    if (NS_TABLE.equals(ns)) {
                        if ("table-row".equals(local)) {
                            inRow = true;
                            currentRowCells = new ArrayList<String>(8);
                            continue;
                        }
                        if ("table-cell".equals(local)) {
                            if (inRow) {
                                inCell = true;
                                currentCell = new StringBuilder(256);
                            }
                            continue;
                        }
                    }

                    // ---- Text blocks ----
                    if (NS_TEXT.equals(ns)) {
                        if ("p".equals(local) || "h".equals(local)) {
                            inParagraphLike = true;
                            continue;
                        }

                        if ("tab".equals(local)) {
                            currentTarget(inCell, currentCell, out).append('\t');
                            continue;
                        }

                        if ("line-break".equals(local)) {
                            currentTarget(inCell, currentCell, out).append('\n');
                            continue;
                        }

                        // <text:s text:c="N"/> : repeated spaces
                        if ("s".equals(local)) {
                            int count = 1;
                            String c = getAttrAny(r, NS_TEXT, "c"); // attribute is usually "text:c"
                            if (c != null) {
                                try {
                                    count = Integer.parseInt(c.trim());
                                } catch (Exception ignore) {
                                }
                            }
                            if (count < 1) count = 1;
                            StringBuilder tgt = currentTarget(inCell, currentCell, out);
                            for (int i = 0; i < count; i++) tgt.append(' ');
                            continue;
                        }
                    }

                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    String txt = r.getText();
                    if (txt != null && !txt.isEmpty()) {
                        currentTarget(inCell, currentCell, out).append(txt);
                    }

                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String ns = r.getNamespaceURI();
                    String local = r.getLocalName();

                    // End of paragraph-like blocks => newline
                    if (NS_TEXT.equals(ns) && ("p".equals(local) || "h".equals(local))) {
                        if (inParagraphLike) {
                            currentTarget(inCell, currentCell, out).append('\n');
                            inParagraphLike = false;
                        }
                        continue;
                    }

                    // End cell => store trimmed cell text
                    if (NS_TABLE.equals(ns) && "table-cell".equals(local)) {
                        if (inCell && currentRowCells != null && currentCell != null) {
                            currentRowCells.add(trimTrailingNewlines(currentCell.toString()));
                            currentCell = null;
                            inCell = false;
                        }
                        continue;
                    }

                    // End row => join cells with tabs, append newline
                    if (NS_TABLE.equals(ns) && "table-row".equals(local)) {
                        if (inRow && currentRowCells != null) {
                            for (int i = 0; i < currentRowCells.size(); i++) {
                                if (i > 0) out.append('\t');
                                out.append(currentRowCells.get(i));
                            }
                            out.append('\n');

                            currentRowCells = null;
                            inRow = false;
                        }
                    }
                }
            }
        } finally {
            try {
                r.close();
            } catch (Exception ignore) {
            }
        }

        return out.toString();
    }

    private static StringBuilder currentTarget(boolean inCell, StringBuilder cell, StringBuilder out) {
        return (inCell && cell != null) ? cell : out;
    }

    private static void trySet(XMLInputFactory f, String key, Object value) {
        try {
            f.setProperty(key, value);
        } catch (Exception ignore) {
        }
    }

    private static String getAttrAny(XMLStreamReader r, String ns, String localName) {
        String v = r.getAttributeValue(ns, localName);
        if (v != null) return v;
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) return r.getAttributeValue(i);
        }
        return null;
    }

    private static String trimTrailingNewlines(String s) {
        int i = s.length();
        while (i > 0) {
            char c = s.charAt(i - 1);
            if (c == '\n' || c == '\r') i--;
            else break;
        }
        return (i == s.length()) ? s : s.substring(0, i);
    }
}
