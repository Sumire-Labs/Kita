package com.sumirelabs.kita.audio.crossfade;

import static org.junit.jupiter.api.Assertions.*;
import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory;
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CrossfadePrototypeTest {
    static class Tone extends BaseAudioTrack {
        private final float frequency;
        private final java.util.concurrent.CountDownLatch stalled;
        private final int totalFrames;
        Tone(float frequency) { this(frequency, null); }
        Tone(float frequency, java.util.concurrent.CountDownLatch stalled) {
            this(frequency, stalled, 150);
        }
        Tone(float frequency, java.util.concurrent.CountDownLatch stalled, int totalFrames) {
            super(new AudioTrackInfo("tone", "test", 3000, "tone", false, null));
            this.frequency = frequency; this.stalled = stalled; this.totalFrames = totalFrames;
        }
        @Override public AudioSourceManager getSourceManager() { return null; }
        @Override protected AudioTrack makeShallowClone() { return new Tone(frequency); }
        @Override public void process(LocalAudioTrackExecutor executor) {
            var pipeline = AudioPipelineFactory.create(executor.getProcessingContext(), new PcmFormat(2, 48000));
            try {
                executor.executeProcessingLoop(() -> {
                    var samples = new float[2][960];
                    for (int frame = 0; frame < totalFrames; frame++) {
                        if (frame == 25 && stalled != null) { stalled.countDown(); Thread.sleep(350); }
                        for (int index = 0; index < 960; index++) samples[0][index] = samples[1][index] = .25f * (float) Math.sin(2 * Math.PI * frequency * (frame * 960 + index) / 48000);
                        pipeline.process(samples, 0, 960);
                    }
                    pipeline.flush();
                }, null);
            } finally { pipeline.close(); }
        }
    }
    @Test void aBriefUnderrunAfterOverlapStartsNeverDiscardsOrRewindsTheNextTrack() throws Exception {
        var source = new CrossfadeSource(); var output = new DefaultAudioPlayerManager();
        output.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE);
        var player = output.createPlayer(); var stalled = new java.util.concurrent.CountDownLatch(1);
        try {
            var mixed = new CrossfadeTrack(source, new Tone(440), "test", "A", 1);
            assertTrue(mixed.stream.prepare(new Tone(880, stalled), "B", "A"));
            assertTrue(stalled.await(3, TimeUnit.SECONDS)); player.playTrack(mixed);
            int frames = 0;
            while (true) {
                var frame = player.provide(5, TimeUnit.SECONDS);
                if (frame == null || frame.isTerminator()) break;
                frames++;
            }
            assertEquals(250, frames, 1, "A brief network stall must not abandon the already audible second track");
            assertEquals("B", mixed.stream.audible(5000).token());
        } finally { player.destroy(); output.shutdown(); source.shutdown(); }
    }
    @Test void twoRealLavaplayerDecodersOverlapWithoutEncodingBetweenDecks() throws Exception {
        overlap(false);
    }
    @Test void earlyEndOfAlreadyAudibleNextTrackStillCommitsItsQueueTransition() throws Exception {
        var source = new CrossfadeSource(); var output = new DefaultAudioPlayerManager();
        output.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE); var player = output.createPlayer();
        try {
            var mixed = new CrossfadeTrack(source, new Tone(440), "test", "A", 1);
            assertTrue(mixed.stream.prepare(new Tone(880, null, 25), "B", "A")); player.playTrack(mixed);
            while (player.provide(5, TimeUnit.SECONDS) != null) { }
            assertTrue(mixed.stream.done()); assertEquals("B", mixed.stream.audible(mixed.getPosition()).token());
            assertEquals(2500, mixed.getPosition(), 40);
        } finally { player.destroy(); output.shutdown(); source.shutdown(); }
    }
    @Test void mixedOutputPassesThroughHrirAsOneContinuousStream() throws Exception { overlap(true); }
    @Test void sourceMetadataDrivesFadeWhenDecoderDurationIsUnknown() throws Exception { overlap(false, true); }
    private void overlap(boolean hrir) throws Exception {
        overlap(hrir, false);
    }
    private void overlap(boolean hrir, boolean unknownDuration) throws Exception {
        var source = new CrossfadeSource(); var output = new DefaultAudioPlayerManager();
        output.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE);
        var player = output.createPlayer();
        if (hrir) player.setFilterFactory((track, format, sink) -> java.util.List.of(new com.sumirelabs.kita.audio.HrirFilter(
                new com.sumirelabs.kita.audio.ConvolutionKernel(new float[][]{{.8f, .1f}, {0, 0}, {0, 0}, {.8f, .1f}}), sink)));
        try {
            Tone first = unknownDuration ? new Tone(440) { @Override public long getDuration() { return Long.MAX_VALUE; } } : new Tone(440);
            var mixed = new CrossfadeTrack(source, first, "test", "A", 1);
            assertTrue(mixed.stream.prepare(new Tone(880), "B", "A"));
            player.playTrack(mixed); var samples = new ArrayList<Float>();
            while (true) {
                var frame = player.provide(5, TimeUnit.SECONDS);
                if (frame == null || frame.isTerminator()) break;
                var bytes = ByteBuffer.wrap(frame.getData()).order(ByteOrder.LITTLE_ENDIAN);
                while (bytes.hasRemaining()) samples.add(bytes.getShort() / 32768f);
                if (samples.size() == 96_000) {
                    player.setPaused(true); long position = mixed.getPosition();
                    for (int attempt = 0; attempt < 50; attempt++) assertNull(player.provide());
                    assertEquals(position, mixed.getPosition()); assertEquals("A", mixed.stream.audible(position).token());
                    assertFalse(mixed.stream.fading(position)); player.setPaused(false);
                }
            }
            assertEquals(5 * 48000 * 2, samples.size(), 1920);
            assertEquals("B", mixed.stream.audible(5000).token());
            assertTrue(samples.stream().allMatch(sample -> Math.abs(sample) < 1));
            // Both frequencies must be audible within the one-second overlap.
            assertTrue(energy(samples, 440, 2500) > .03); assertTrue(energy(samples, 880, 2500) > .03);
        } finally { player.destroy(); output.shutdown(); source.shutdown(); }
    }
    @Test void equalPowerOverlapLimitsCorrelatedPeaks() {
        var first = new float[][]{{1, 1}, {1, 1}}; var output = new float[2][2];
        CrossfadeMixer.mix(first, first, output, 50, 100, 2);
        assertTrue(output[0][0] < 1); assertTrue(output[0][0] > .9);
    }
    private double energy(ArrayList<Float> samples, int frequency, int milliseconds) {
        double real = 0, imaginary = 0; int start = milliseconds * 48;
        for (int index = 0; index < 4800; index++) {
            double value = samples.get((start + index) * 2), phase = 2 * Math.PI * frequency * index / 48000;
            real += value * Math.cos(phase); imaginary += value * Math.sin(phase);
        }
        return Math.hypot(real, imaginary) / 4800;
    }
}
