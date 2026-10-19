package org.example.openccjavafx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlainTextDecodingTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16LE", "UTF-16BE"})
    void autoDetectionDecodesUnicodeAndRemovesBom(String charsetName) {
        String text = "中文测试，日本語テスト。";
        byte[] bytes = ("\uFEFF" + text).getBytes(Charset.forName(charsetName));

        assertEquals(text, OpenccJavaFxController.decodePlainText(bytes, true));
    }

    @Test
    void disabledDetectionUsesUtf8() {
        String text = "中文测试";
        assertEquals(text, OpenccJavaFxController.decodePlainText(
                text.getBytes(StandardCharsets.UTF_8), false));
        byte[] utf16 = ("\uFEFF" + text).getBytes(StandardCharsets.UTF_16LE);
        assertNotEquals(text, OpenccJavaFxController.decodePlainText(utf16, false));
    }

    @Test
    void emptyInputRemainsEmpty() {
        assertEquals("", OpenccJavaFxController.decodePlainText(new byte[0], true));
        assertEquals("", OpenccJavaFxController.decodePlainText(new byte[0], false));
    }
}
