package com.sumirelabs.kita.logshare;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Message;

final class LogContent {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final int MAX_LINES = 25_000;
    private LogContent() {}
    static String download(Message.Attachment attachment) throws Exception {
        if (attachment.getSize() > MAX_BYTES) throw new IllegalArgumentException("ログは10MiB以下にしてください。");
        try (var stream = attachment.getProxy().download().get(20, TimeUnit.SECONDS)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("ログは10MiB以下にしてください。");
            return decode(bytes);
        }
    }
    static String decode(byte[] bytes) {
        var charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            charset = StandardCharsets.UTF_16LE; offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            charset = StandardCharsets.UTF_16BE; offset = 2;
        } else if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) offset = 3;
        try {
            String text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("バイナリファイルは共有できません。");
            return text.replace("\r\n", "\n").replace('\r', '\n');
        } catch (CharacterCodingException error) { throw new IllegalArgumentException("UTF-8またはBOM付きUTF-16のログを添付してください。"); }
    }
    static void validate(String text, int maxBytes, int maxLines) {
        if (text.isBlank()) throw new IllegalArgumentException("ログが空です。");
        if (text.getBytes(StandardCharsets.UTF_8).length > Math.min(MAX_BYTES, maxBytes)
                || text.lines().limit((long) Math.min(MAX_LINES, maxLines) + 1).count() > Math.min(MAX_LINES, maxLines)) {
            throw new IllegalArgumentException("ログが共有上限を超えています。内容を省略せずに共有するため、分割してください。");
        }
    }
}
