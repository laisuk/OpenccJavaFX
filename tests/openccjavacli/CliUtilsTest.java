package openccjavacli;

import openccjava.CustomDictSpec;
import openccjava.DictSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliUtilsTest {

    @Test
    void cliUsesTheCorePortableParserContract() {
        CustomDictSpec spec = CustomDictSpec.parse(
                "hkphrasesrev:append:C:\\data\\custom.txt"
        );

        assertEquals(DictSlot.HKPhrasesRev, spec.slot);
        assertEquals(Paths.get("C:\\data\\custom.txt"), spec.paths.get(0));
    }
}
