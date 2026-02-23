package openxmlhelper;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class EpubHelper {

    private EpubHelper() {
    }

    // ---------------------------- Format detection ----------------------------

    public static boolean isEpub(File file) {
        if (file == null) return false;
        if (!file.exists() || !file.isFile()) return false;

        String name = file.getName();
        if (!endsWithIgnoreCase(name, ".epub")) return false;

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            return zip.getEntry("META-INF/container.xml") != null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            closeQuietly(zip);
        }
    }

    // ---------------------------- EPUB extraction ----------------------------

    public static String extractEpubAllText(
            String epubPath,
            boolean includePartHeadings,
            boolean normalizeNewlines,
            boolean skipNavDocuments
    ) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(epubPath);

            String opfPath = findOpfPath(zip);
            if (opfPath == null) {
                throw new IllegalStateException("container.xml has no OPF rootfile. Not a valid .epub?");
            }

            String opfDir = getDir(opfPath);

            OpfData opf = loadOpf(zip, opfPath);

            StringBuilder sb = new StringBuilder(256 * 1024);

            for (String idref : opf.spine) {
                ManifestItem item = opf.manifest.get(idref);
                if (item == null) continue;

                if (!looksLikeHtml(item.mediaType, item.href)) continue;
                if (skipNavDocuments && item.isNav) continue;

                String fullName = combineZipPath(opfDir, item.href);
                ZipEntry entry = zip.getEntry(fullName);
                if (entry == null) continue;

                if (includePartHeadings) {
                    if (sb.length() > 0 && !Utils.endsWithNewline(sb)) sb.append('\n');
                    sb.append("=== ").append(fullName).append(" ===\n");
                }

                InputStream in = null;
                try {
                    in = zip.getInputStream(entry);
                    String chapterText = extractXhtmlText(in);
                    sb.append(chapterText);
                } finally {
                    closeQuietly(in);
                }

                if (!endsWithNewline(sb)) sb.append('\n');
                sb.append('\n'); // blank line between spine docs
            }

            String text = sb.toString();
            if (normalizeNewlines) {
                text = text.replace("\r\n", "\n").replace("\r", "\n");
            }

            text = normalizeExcessBlankLines(text);
            return text;

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(zip);
        }
    }

    // ---------------------------- container.xml ----------------------------

    private static String findOpfPath(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/container.xml");
        if (entry == null) return null;

        InputStream in = null;
        try {
            in = zip.getInputStream(entry);

            org.w3c.dom.Document doc = parseXmlSecure(in, true);

            org.w3c.dom.NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                org.w3c.dom.Node n = all.item(i);
                if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

                String local = localNameOf(n);
                if (!"rootfile".equals(local)) continue;

                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                String full = e.getAttribute("full-path");
                if (full.trim().isEmpty()) {
                    full = e.getAttribute("fullpath");
                }
                if (!full.trim().isEmpty()) {
                    return full.trim();
                }
            }
            return null;
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(in);
        }
    }
    // ---------------------------- OPF parsing ----------------------------

    private static final class ManifestItem {
        String href = "";
        String mediaType = "";
        boolean isNav;
    }

    private static final class OpfData {
        final Map<String, ManifestItem> manifest;
        final List<String> spine;

        OpfData(Map<String, ManifestItem> manifest, List<String> spine) {
            this.manifest = manifest;
            this.spine = spine;
        }
    }

    private static OpfData loadOpf(ZipFile zip, String opfPath) throws IOException {
        ZipEntry entry = zip.getEntry(opfPath);
        if (entry == null) {
            throw new IllegalStateException("OPF not found: " + opfPath);
        }

        Map<String, ManifestItem> manifest = new HashMap<>(1024);
        List<String> spine = new ArrayList<>(256);

        InputStream in = null;
        try {
            in = zip.getInputStream(entry);

            // C# uses DtdProcessing.Ignore + XmlResolver=null.
            // In Java, we disable external entity expansion and allow parsing even if DOCTYPE exists.
            Document doc = parseXmlSecure(in, /*prohibitDtd*/ false);

            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Node n = all.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;

                String local = localNameOf(n);
                Element e = (Element) n;

                if ("item".equals(local)) {
                    String id = e.getAttribute("id");
                    String href = e.getAttribute("href");
                    String mt = e.getAttribute("media-type");

                    if (notBlank(id) && notBlank(href)) {
                        String props = e.getAttribute("properties");
                        boolean isNav = false;
                        if (!props.trim().isEmpty()) {
                            String[] tokens = props.trim().split("\\s+");
                            for (String p : tokens) {
                                if ("nav".equalsIgnoreCase(p)) {
                                    isNav = true;
                                    break;
                                }
                            }
                        }

                        ManifestItem item = new ManifestItem();
                        item.href = href;
                        item.mediaType = mt;
                        item.isNav = isNav;
                        manifest.put(id, item);
                    }
                } else if ("itemref".equals(local)) {
                    String idref = e.getAttribute("idref");
                    if (notBlank(idref)) {
                        spine.add(idref.trim());
                    }
                }
            }

            return new OpfData(manifest, spine);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(in);
        }
    }

    private static boolean looksLikeHtml(String mediaType, String href) {
        if (mediaType == null) mediaType = "";
        if (href == null) href = "";

        if (mediaType.isEmpty()) {
            return endsWithIgnoreCase(href, ".xhtml")
                    || endsWithIgnoreCase(href, ".html")
                    || endsWithIgnoreCase(href, ".htm");
        }

        if ("application/xhtml+xml".equalsIgnoreCase(mediaType)) return true;
        if ("text/html".equalsIgnoreCase(mediaType)) return true;

        // Java 8: no String.containsIgnoreCase, so lower-case it
        if (mediaType.toLowerCase(Locale.ROOT).contains("html")) return true;

        return endsWithIgnoreCase(href, ".xhtml")
                || endsWithIgnoreCase(href, ".html")
                || endsWithIgnoreCase(href, ".htm");
    }

    // ---------------------------- XHTML -> plain text ----------------------------

    private static String extractXhtmlText(InputStream xhtmlStream) {
        String html;
        try {
            byte[] bytes = readAllBytes(xhtmlStream);
            html = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        doc.select("script, style, head, svg, math, noscript").remove();

        final StringBuilder sb = new StringBuilder(32 * 1024);

        doc.body();
        org.jsoup.select.NodeTraversor.traverse(new org.jsoup.select.NodeVisitor() {
            @Override
            public void head(org.jsoup.nodes.Node node, int depth) {
                if (node instanceof org.jsoup.nodes.Element) {
                    String name = ((org.jsoup.nodes.Element) node).tagName();
                    if (isBlockElement(name)) ensureParagraphBreak(sb);
                    else if ("br".equalsIgnoreCase(name)) sb.append('\n');
                } else if (node instanceof org.jsoup.nodes.TextNode) {
                    String t = ((org.jsoup.nodes.TextNode) node).text();
                    if (!t.isEmpty()) appendNormalizedText(sb, t);
                }
            }

            @Override
            public void tail(org.jsoup.nodes.Node node, int depth) {
                if (node instanceof org.jsoup.nodes.Element) {
                    String name = ((org.jsoup.nodes.Element) node).tagName();
                    if (isBlockElement(name)) ensureParagraphBreak(sb);
                }
            }
        }, doc.body());

        String text = sb.toString();
        text = text.replace("\u00AD", "");
        text = text.replace("\u00A0", " ");
        return text;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(32 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static String stripDoctype(String s) {
        // remove <!DOCTYPE ...> (including internal subset)
        return s.replaceAll("(?is)<!DOCTYPE[^>]*?(\\[[\\s\\S]*?])?\\s*>", "");
    }

    private static String replaceCommonEntities(String s) {
        // Minimal set to stop XML parser choking; extend as needed.
        // Use numeric entities so XML parser accepts without DTD.
        return s
                .replace("&nbsp;", "&#160;")
                .replace("&ensp;", "&#8194;")
                .replace("&emsp;", "&#8195;")
                .replace("&thinsp;", "&#8201;")
                .replace("&hellip;", "&#8230;")
                .replace("&mdash;", "&#8212;")
                .replace("&ndash;", "&#8211;")
                .replace("&lsquo;", "&#8216;")
                .replace("&rsquo;", "&#8217;")
                .replace("&ldquo;", "&#8220;")
                .replace("&rdquo;", "&#8221;")
                .replace("&laquo;", "&#171;")
                .replace("&raquo;", "&#187;")
                .replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;");
    }

    // A minimal "marker node" to simulate end-element handling without recursion.
    private static final class PostBlockNode implements Node {
        private final String name;

        PostBlockNode(String name) {
            this.name = name;
        }

        @Override
        public String getNodeName() {
            return name;
        }

        @Override
        public String getNodeValue() {
            return null;
        }

        @Override
        public void setNodeValue(String nodeValue) {
        }

        @Override
        public short getNodeType() {
            return -999;
        }

        @Override
        public Node getParentNode() {
            return null;
        }

        @Override
        public NodeList getChildNodes() {
            return EMPTY_NODELIST;
        }

        @Override
        public Node getFirstChild() {
            return null;
        }

        @Override
        public Node getLastChild() {
            return null;
        }

        @Override
        public Node getPreviousSibling() {
            return null;
        }

        @Override
        public Node getNextSibling() {
            return null;
        }

        @Override
        public NamedNodeMap getAttributes() {
            return null;
        }

        @Override
        public Document getOwnerDocument() {
            return null;
        }

        @Override
        public Node insertBefore(Node newChild, Node refChild) {
            return null;
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) {
            return null;
        }

        @Override
        public Node removeChild(Node oldChild) {
            return null;
        }

        @Override
        public Node appendChild(Node newChild) {
            return null;
        }

        @Override
        public boolean hasChildNodes() {
            return false;
        }

        @Override
        public Node cloneNode(boolean deep) {
            return null;
        }

        @Override
        public void normalize() {
        }

        @Override
        public boolean isSupported(String feature, String version) {
            return false;
        }

        @Override
        public String getNamespaceURI() {
            return null;
        }

        @Override
        public String getPrefix() {
            return null;
        }

        @Override
        public void setPrefix(String prefix) {
        }

        @Override
        public String getLocalName() {
            return name;
        }

        @Override
        public boolean hasAttributes() {
            return false;
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public short compareDocumentPosition(Node other) {
            return 0;
        }

        @Override
        public String getTextContent() {
            return null;
        }

        @Override
        public void setTextContent(String textContent) {
        }

        @Override
        public boolean isSameNode(Node other) {
            return this == other;
        }

        @Override
        public String lookupPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public boolean isDefaultNamespace(String namespaceURI) {
            return false;
        }

        @Override
        public String lookupNamespaceURI(String prefix) {
            return null;
        }

        @Override
        public boolean isEqualNode(Node arg) {
            return false;
        }

        @Override
        public Object getFeature(String feature, String version) {
            return null;
        }

        @Override
        public Object setUserData(String key, Object data, UserDataHandler handler) {
            return null;
        }

        @Override
        public Object getUserData(String key) {
            return null;
        }
    }

    private static final NodeList EMPTY_NODELIST = new NodeList() {
        @Override
        public Node item(int index) {
            return null;
        }

        @Override
        public int getLength() {
            return 0;
        }
    };

    private static boolean isSkipElement(String localName) {
        return "script".equalsIgnoreCase(localName)
                || "style".equalsIgnoreCase(localName)
                || "head".equalsIgnoreCase(localName)
                || "svg".equalsIgnoreCase(localName)
                || "math".equalsIgnoreCase(localName)
                || "noscript".equalsIgnoreCase(localName);
    }

    private static boolean isBlockElement(String localName) {
        return "p".equalsIgnoreCase(localName)
                || "div".equalsIgnoreCase(localName)
                || "section".equalsIgnoreCase(localName)
                || "article".equalsIgnoreCase(localName)
                || "blockquote".equalsIgnoreCase(localName)
                || "li".equalsIgnoreCase(localName)
                || "h1".equalsIgnoreCase(localName)
                || "h2".equalsIgnoreCase(localName)
                || "h3".equalsIgnoreCase(localName)
                || "h4".equalsIgnoreCase(localName)
                || "h5".equalsIgnoreCase(localName)
                || "h6".equalsIgnoreCase(localName)
                || "hr".equalsIgnoreCase(localName);
    }

    private static void appendNormalizedText(StringBuilder sb, String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                if (sb.length() == 0) continue;
                char last = sb.charAt(sb.length() - 1);
                if (last == ' ' || last == '\n' || last == '\r' || last == '\t') continue;
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
    }

    private static void ensureParagraphBreak(StringBuilder sb) {
        trimTrailingSpaces(sb);
        if (sb.length() == 0) return;

        int n = sb.length();
        if (n >= 2 && sb.charAt(n - 1) == '\n' && sb.charAt(n - 2) == '\n') return;

        if (sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        sb.append('\n');
    }

    private static void trimTrailingSpaces(StringBuilder sb) {
        while (sb.length() > 0) {
            char c = sb.charAt(sb.length() - 1);
            if (c == ' ' || c == '\t') {
                sb.setLength(sb.length() - 1);
            } else {
                break;
            }
        }
    }

    private static boolean endsWithNewline(StringBuilder sb) {
        return Utils.endsWithNewline(sb);
    }

    // ---------------------------- Paths / normalize ----------------------------

    private static String getDir(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx + 1);
    }

    private static String combineZipPath(String dir, String href) {
        String raw = (dir == null ? "" : dir) + (href == null ? "" : href);
        raw = raw.replace('\\', '/');

        String[] parts = raw.split("/");
        List<String> stack = new ArrayList<>(parts.length);

        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
                continue;
            }
            stack.add(p);
        }

        // Keep forward slashes for zip entry names
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    private static String normalizeExcessBlankLines(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int nl = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                nl++;
                if (nl <= 2) sb.append(c);
            } else {
                nl = 0;
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------- XML security helpers ----------------------------

    private static Document parseXmlSecure(InputStream in, boolean prohibitDtd)
            throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // Secure processing
        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Throwable ignored) {
        }

        // Block XXE / external entities
        disableXxe(dbf);

        if (prohibitDtd) {
            // Similar to DtdProcessing.Prohibit
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Throwable ignored) {
            }
        } else {
            // Similar to DtdProcessing.Ignore (allow doctype but don't resolve externals)
            try {
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Throwable ignored) {
            }
        }

        DocumentBuilder db = dbf.newDocumentBuilder();
        // No external entity resolution
        try {
            db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        } catch (Throwable ignored) {
        }

        return db.parse(in);
    }

    private static void disableXxe(DocumentBuilderFactory dbf) {
        // The exact supported features vary by JAXP impl, so best-effort.
        String[] features = new String[]{
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        };
        for (String f : features) {
            try {
                dbf.setFeature(f, false);
            } catch (Throwable ignored) {
            }
        }

        try {
            dbf.setXIncludeAware(false);
        } catch (Throwable ignored) {
        }
        try {
            dbf.setExpandEntityReferences(false);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------- misc ----------------------------

    private static String localNameOf(Node n) {
        String ln = n.getLocalName();
        if (ln != null) return ln;
        // fallback for non-namespace-aware nodes
        String name = n.getNodeName();
        int idx = name.indexOf(':');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static boolean endsWithIgnoreCase(String s, String suffix) {
        if (s == null || suffix == null) return false;
        if (s.length() < suffix.length()) return false;
        return s.regionMatches(true, s.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(ZipFile z) {
        if (z == null) return;
        try {
            z.close();
        } catch (Throwable ignored) {
        }
    }
}