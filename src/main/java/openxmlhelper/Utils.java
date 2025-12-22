package openxmlhelper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class Utils {

    private Utils() {
    }

    static boolean endsWithNewline(StringBuilder sb) {
        if (sb.length() == 0)
            return true;

        char c = sb.charAt(sb.length() - 1);
        return c == '\n' || c == '\r';
    }

    static String trimTrailingNewlines(String s) {
        int i = s.length();
        while (i > 0) {
            char c = s.charAt(i - 1);
            if (c == '\n' || c == '\r')
                i--;
            else
                break;
        }
        return (i == s.length()) ? s : s.substring(0, i);
    }

    static String normalizeNewlinesToLf(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    static String readAll(BufferedReader br) throws IOException {
        StringBuilder sb = new StringBuilder(4096);
        char[] buf = new char[8192];
        int n;
        while ((n = br.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    static List<String> distinctIgnoreCasePreserveOrder(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        HashSet<String> seenLower = new HashSet<>(input.size() * 2);
        for (String s : input) {
            String k = s.toLowerCase(Locale.ROOT);
            if (seenLower.add(k)) {
                out.add(s);
            }
        }
        return out;
    }

    static StringBuilder currentTarget(boolean inCell, StringBuilder currentCell, StringBuilder sb) {
        return (inCell && currentCell != null) ? currentCell : sb;
    }

    static void trySet(XMLInputFactory f, String key, Object value) {
        try {
            f.setProperty(key, value);
        } catch (Exception ignore) {
        }
    }

    static String getAttr(XMLStreamReader r, String ns, String localName) {
        String v = r.getAttributeValue(ns, localName);
        if (v != null) return v;

        for (int i = 0; i < r.getAttributeCount(); i++) {
            String ln = r.getAttributeLocalName(i);
            if (localName.equals(ln)) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    static String getAttrAny(XMLStreamReader r, String ns, String localName) {
        String v = r.getAttributeValue(ns, localName);
        if (v != null) return v;
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    static Integer tryParseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    static void skipElement(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT) depth++;
            else if (e == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
