package openxmlhelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

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

        try (InputStream in = zip.getInputStream(entry)) {
            Document doc = Jsoup.parse(in, null, "", Parser.xmlParser());

            // EPUB container.xml: <rootfile full-path="..."/>
            for (Element rf : doc.getElementsByTag("rootfile")) {
                String full = rf.attr("full-path");
                if (full.trim().isEmpty()) {
                    full = rf.attr("fullpath"); // fallback seen in some broken files
                }
                if (!full.trim().isEmpty()) {
                    return full.trim();
                }
            }
            return null;
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
        if (entry == null) throw new IllegalStateException("OPF not found: " + opfPath);

        Map<String, ManifestItem> manifest = new HashMap<>(1024);
        List<String> spine = new ArrayList<>(256);

        try (InputStream in = zip.getInputStream(entry)) {
            Document doc = Jsoup.parse(in, null, "", Parser.xmlParser());

            // manifest: <item id="x" href="y" media-type="..." properties="nav"/>
            for (Element it : doc.getElementsByTag("item")) {
                String id = it.attr("id");
                String href = it.attr("href");
                if (!notBlank(id) || !notBlank(href)) continue;

                ManifestItem mi = new ManifestItem();
                mi.href = href;
                mi.mediaType = it.attr("media-type");

                String props = it.attr("properties");
                mi.isNav = containsTokenIgnoreCase(props, "nav");

                manifest.put(id.trim(), mi);
            }

            // spine: <itemref idref="..."/>
            for (Element ir : doc.getElementsByTag("itemref")) {
                String idref = ir.attr("idref");
                if (notBlank(idref)) spine.add(idref.trim());
            }
        }

        return new OpfData(manifest, spine);
    }

    private static boolean containsTokenIgnoreCase(String spaceSeparated, String token) {
        if (spaceSeparated == null) return false;
        String s = spaceSeparated.trim();
        if (s.isEmpty()) return false;
        String[] parts = s.split("\\s+");
        for (String p : parts) {
            if (p.equalsIgnoreCase(token)) return true;
        }
        return false;
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

        Document doc = org.jsoup.Jsoup.parse(html);
        doc.select("script, style, head, svg, math, noscript").remove();

        final StringBuilder sb = new StringBuilder(32 * 1024);

        doc.body();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Element) {
                    String name = ((Element) node).tagName();
                    if (isBlockElement(name)) ensureParagraphBreak(sb);
                    else if ("br".equalsIgnoreCase(name)) sb.append('\n');
                } else if (node instanceof TextNode) {
                    String t = ((TextNode) node).text();
                    if (!t.isEmpty()) appendNormalizedText(sb, t);
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element) {
                    String name = ((Element) node).tagName();
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

    // ---------------------------- misc ----------------------------

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