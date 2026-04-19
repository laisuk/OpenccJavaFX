package openccjava;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OpenccApiRegressionTest {
    @Test
    void fromJsonAcceptsLegacyTwoElementDictEntries() throws Exception {
        String json = "{\"st_characters\":[{\"汉\":\"漢\"},1]}";

        DictionaryMaxlength dictionary = DictionaryMaxlength.fromJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );

        assertNotNull(dictionary.st_characters);
        assertEquals("漢", dictionary.st_characters.dict.get("汉"));
        assertEquals(1, dictionary.st_characters.maxLength);
        assertEquals(1, dictionary.st_characters.minLength);
    }

    @Test
    void officeConvertFailsWhenOutputFileIsNull() {
        OpenCC converter = new OpenCC("s2t");

        OfficeHelper.FileResult result = OfficeHelper.convert(
                new File("unused-input.docx"),
                null,
                "docx",
                converter,
                false,
                false
        );

        assertFalse(result.success);
        assertTrue(result.message.contains("Output file must not be null"));
    }
}
