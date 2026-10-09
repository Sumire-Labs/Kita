package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogContentTest {
    @Test void handlesWindowsLineEndingsUtf8BomAndUtf16Bom() {
        assertEquals("一行\n二行\n", LogContent.decode("\ufeff一行\r\n二行\r".getBytes(StandardCharsets.UTF_8)));
        byte[] body = "一行\r\n二行".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2]; bytes[0] = (byte) 0xff; bytes[1] = (byte) 0xfe;
        System.arraycopy(body, 0, bytes, 2, body.length);
        assertEquals("一行\n二行", LogContent.decode(bytes));
    }
    @Test void rejectsBinaryAndInvalidUtf8() {
        assertThrows(IllegalArgumentException.class, () -> LogContent.decode(new byte[]{0, 1, 2}));
        assertThrows(IllegalArgumentException.class, () -> LogContent.decode(new byte[]{(byte) 0xff}));
    }
    @Test void countsUtf8BytesAndRejectsOversizeInsteadOfTruncating() {
        LogContent.validate("日本語", 9, 1);
        assertThrows(IllegalArgumentException.class, () -> LogContent.validate("日本語", 8, 1));
        LogContent.validate("one\ntwo\n", 100, 2);
        assertThrows(IllegalArgumentException.class, () -> LogContent.validate("one\ntwo\nthree", 100, 2));
        assertThrows(IllegalArgumentException.class, () -> LogContent.validate("  ", 100, 2));
    }
}
