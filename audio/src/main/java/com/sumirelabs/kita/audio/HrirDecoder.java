package com.sumirelabs.kita.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class HrirDecoder {
    private HrirDecoder() {}
    public static float[][] decode(Path wav, int sampleRate) throws Exception {
        if (Files.size(wav) > 16_777_216) throw new IllegalArgumentException("HRIR file exceeds 16 MiB");
        var info = Files.createTempFile("kita-hrir-", ".txt");
        var output = Files.createTempFile("kita-hrir-", ".pcm");
        try {
            run(new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
                    "stream=sample_rate,channels", "-of", "csv=p=0", wav.toString()).redirectOutput(info.toFile()));
            var fields = Files.readString(info).strip().split(",");
            int originalRate = Integer.parseInt(fields[0]);
            int channels = Integer.parseInt(fields[1]);
            var mapping = switch (channels) {
                case 14 -> "pan=4c|c0=c0|c1=c1|c2=c8|c3=c7";
                case 4 -> "pan=4c|c0=c0|c1=c1|c2=c2|c3=c3";
                case 2 -> "pan=4c|c0=c0|c1=c1|c2=c1|c3=c0";
                default -> throw new IllegalArgumentException("Expected a 2, 4 or 14 channel HRIR");
            };
            run(new ProcessBuilder("ffmpeg", "-v", "error", "-nostdin", "-y", "-i", wav.toString(),
                    "-af", mapping + ",aresample=" + sampleRate, "-f", "f32le", "-acodec", "pcm_f32le", output.toString()));
            long size = Files.size(output);
            if (size == 0 || size % 16 != 0 || size > 1_048_576) throw new IllegalArgumentException("HRIR must contain 1–65536 frames");
            int frames = (int) size / 16;
            var buffer = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
            float[][] impulses = new float[4][frames];
            float rateGain = (float) originalRate / sampleRate;
            for (int frame = 0; frame < frames; frame++) {
                for (int channel = 0; channel < 4; channel++) {
                    float value = buffer.getFloat() * rateGain;
                    if (!Float.isFinite(value)) throw new IllegalArgumentException("HRIR contains non-finite samples");
                    impulses[channel][frame] = value;
                }
            }
            return impulses;
        } finally { Files.deleteIfExists(info); Files.deleteIfExists(output); }
    }

    private static void run(ProcessBuilder builder) throws Exception {
        var process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw new IllegalStateException("HRIR decoding timed out");
            if (process.exitValue() != 0) throw new IllegalArgumentException("Unsupported HRIR WAV file");
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
