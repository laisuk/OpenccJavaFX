package openccjava;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MiniDictJsonTest {
    private DictionaryMaxlength.DictEntry entry(String map, String metadata) {
        return MiniDictJson.parseToMap("{\"st_phrases\":[{" + map + "}," + metadata + "]}").get("st_phrases");
    }

    @Test void stringsAndUtf16() {
        String[] encoded = {"plain", "前缀\\\"尾", "前\\\\后", "a\\/b", "\\n\\t\\r\\b\\f",
                "\\u6c49\\u5B57", "\\uD840\\uDC00", "𠀀汉字", "prefix\\n中\\t尾"};
        String[] decoded = {"plain", "前缀\"尾", "前\\后", "a/b", "\n\t\r\b\f", "汉字", "𠀀", "𠀀汉字", "prefix\n中\t尾"};
        for (int n = 0; n < encoded.length; n++) {
            assertEquals(decoded[n], entry("\"key\":\"" + encoded[n] + "\"", "4,1").dict.get("key"));
            assertEquals("value", entry("\"" + encoded[n] + "\":\"value\"", "4,1").dict.get(decoded[n]));
        }
    }

    @Test void rejectsMalformedStrings() {
        for (String token : new String[]{"\"unterminated", "\"bad\\", "\"\\x\"", "\"\\u12\"", "\"\\uGGGG\""})
            assertThrows(IllegalArgumentException.class, () -> entry("\"k\":" + token, "1"));
        for (int c = 0; c < 32; c++) {
            String value = "\"k\":\"prefix" + (char)c + "tail\"";
            assertThrows(IllegalArgumentException.class, () -> entry(value, "1"));
            String escapedPrefix = "\"k\":\"\\n" + (char)c + "\"";
            assertThrows(IllegalArgumentException.class, () -> entry(escapedPrefix, "1"));
        }
    }

    @Test void integersAndMetadata() {
        for (String n : new String[]{"0", "1", "00012", "2147483647", "0000000000000000000000000"})
            assertEquals(Integer.parseInt(n), entry("", n + ",0").maxLength);
        for (String n : new String[]{"2147483648", "99999999999999999999", "-1", "1.0", "١", "１２", "1١"})
            assertThrows(IllegalArgumentException.class, () -> entry("", n));
        assertEquals(0, entry("", "0").minLength);
        assertEquals(1, entry("\"汉\":\"漢\"", "2").minLength);
        assertEquals(2, entry("\"后台\":\"後台\"", "2,2").minLength);
        assertEquals(7, entry("", "0,7").minLength); // Preserve existing metadata interpretation.
        assertThrows(IllegalArgumentException.class, () -> entry("", "1,2"));
    }

    @Test void structureOrderAndDuplicates() {
        assertTrue(MiniDictJson.parseToMap(" {} \n").isEmpty());
        assertTrue(entry("", "0,0").dict.isEmpty());
        DictionaryMaxlength.DictEntry e = entry("\"z\":\"一\",\"a\":\"二\",\"z\":\"三\"", "1,1");
        assertEquals(Arrays.asList("z", "a"), new ArrayList<>(e.dict.keySet()));
        assertEquals("三", e.dict.get("z"));
        Map<String, DictionaryMaxlength.DictEntry> m = MiniDictJson.parseToMap("{\"b\":[{},1],\"a\":[{},2],\"b\":[{},3]}");
        assertEquals(Arrays.asList("b", "a"), new ArrayList<>(m.keySet()));
        assertEquals(3, m.get("b").maxLength);
        for (String json : new String[]{"{\"x\":[{}]}", "{\"x\":[{},1,1,1]}", "{}x", "{\"x\"[{},1]}",
                "{\"x\":[{},1,]}", "{\"x\":[{},1],}", "{\"x\":[{\"a\":\"b\",},1]}", "{\"x\":[{} 1]}", "\uFEFF{}"})
            assertThrows(IllegalArgumentException.class, () -> MiniDictJson.parseToMap(json));
    }

    @Test void streamClosesOnSuccessAndFailure() throws Exception {
        for (String json : new String[]{"{}", "bad"}) {
            final boolean[] closed = {false};
            InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)) {
                @Override public void close() { closed[0] = true; }
            };
            if (json.equals("{}")) assertTrue(MiniDictJson.parseToMap(in).isEmpty());
            else assertThrows(IllegalArgumentException.class, () -> MiniDictJson.parseToMap(in));
            assertTrue(closed[0]);
        }
    }

    @Test void preservesUnsortedSerializationOrder() throws Exception {
        DictionaryMaxlength d = DictionaryMaxlength.fromJsonStringNoDeps(
                "{\"st_phrases\":[{\"z\":\"one\",\"a\":\"two\",\"z\":\"three\"},1,1]}");
        String json = d.serializeToJsonString(false, false);
        assertTrue(json.indexOf("\"z\"") < json.indexOf("\"a\""));
        assertEquals("three", d.st_phrases.dict.get("z"));
    }

    @Test void streamClosesOnReadFailure() {
        final boolean[] closed = {false};
        InputStream in = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(IOException.class, () -> MiniDictJson.parseToMap(in));
        assertTrue(closed[0]);
    }

    @Test void largeSnapshot() {
        StringBuilder map = new StringBuilder();
        for (int n = 0; n < 30000; n++) {
            if (n != 0) map.append(',');
            map.append('"').append("词").append(n).append("\":\"詞").append(n).append('"');
        }
        DictionaryMaxlength.DictEntry e = entry(map.toString(), "6,2");
        assertEquals(30000, e.dict.size());
        assertEquals("詞29999", e.dict.get("词29999"));
    }
}
