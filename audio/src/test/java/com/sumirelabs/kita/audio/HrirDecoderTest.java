package com.sumirelabs.kita.audio;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_FFMPEG", matches = "true")
class HrirDecoderTest {
    @TempDir Path directory;
    @Test void mapsHesuviExtensibleFourteenChannelFloatWav() throws Exception {
        var wav = fixture(14, 48000, true);
        var impulses = HrirDecoder.decode(wav, 48000);
        assertEquals(4, impulses.length);
        assertEquals(.01f, impulses[0][64], 1e-6);
        assertEquals(.02f, impulses[1][64], 1e-6);
        assertEquals(.09f, impulses[2][64], 1e-6);
        assertEquals(.08f, impulses[3][64], 1e-6);
    }
    @Test void decodesSymmetricTwoChannelAndResamples44100() throws Exception {
        var impulses = HrirDecoder.decode(fixture(2, 44100, false), 48000);
        assertEquals(4, impulses.length);
        assertTrue(impulses[0].length > 256);
        assertArrayEquals(impulses[0], impulses[3]);
        assertArrayEquals(impulses[1], impulses[2]);
        double sum = 0;
        for (var sample : impulses[0]) sum += sample;
        assertEquals(.01, sum, 1e-4, "Resampling must preserve DC gain");
    }
    @Test void rejectsUnexpectedChannelLayout() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> HrirDecoder.decode(fixture(6, 48000, false), 48000));
    }
    @Test void mirrorsHesuviSevenChannelFrontEarPair() throws Exception {
        var impulses = HrirDecoder.decode(fixture(7, 48000, true), 48000);
        assertEquals(.01f, impulses[0][64], 1e-6);
        assertEquals(.02f, impulses[1][64], 1e-6);
        assertArrayEquals(impulses[0], impulses[3]);
        assertArrayEquals(impulses[1], impulses[2]);
    }
    private Path fixture(int channels, int rate, boolean extensible) throws Exception {
        int frames = 256; int formatSize = extensible ? 40 : 16; int dataSize = frames * channels * 4;
        var buffer = ByteBuffer.allocate(12 + 8 + formatSize + 8 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(buffer.capacity() - 8);
        buffer.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(formatSize);
        buffer.putShort((short) (extensible ? 0xFFFE : 3)).putShort((short) channels).putInt(rate);
        buffer.putInt(rate * channels * 4).putShort((short) (channels * 4)).putShort((short) 32);
        if (extensible) {
            buffer.putShort((short) 22).putShort((short) 32).putInt(0);
            buffer.putInt(3).putShort((short) 0).putShort((short) 0x10);
            buffer.put(new byte[]{(byte) 0x80, 0, 0, (byte) 0xAA, 0, 0x38, (byte) 0x9B, 0x71});
        }
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(dataSize);
        for (int frame = 0; frame < frames; frame++) for (int channel = 0; channel < channels; channel++) {
            buffer.putFloat(frame == 64 ? (channel + 1) * .01f : 0);
        }
        var path = directory.resolve(channels + "-" + rate + ".wav");
        Files.write(path, buffer.array());
        return path;
    }
}
