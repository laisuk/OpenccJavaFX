package openccjava;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficeHelperTest {

    @Test
    void officeFormatsIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                OfficeHelper.OFFICE_FORMATS.set(0, "changed"));
    }

    @Test
    void resultMessageMustNotBeNull() {
        assertThrows(NullPointerException.class, () ->
                new OfficeHelper.FileResult(true, null));
    }

    @Test
    void memoryResultDefensivelyCopiesInputData() {
        byte[] input = {1, 2, 3};
        OfficeHelper.MemoryResult result = new OfficeHelper.MemoryResult(true, "ok", input);

        input[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, result.data);
    }
}
