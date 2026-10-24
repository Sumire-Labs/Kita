package com.sumirelabs.kita.audio.crossfade;

import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory;
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

final class CrossfadeTrack extends BaseAudioTrack {
    private final CrossfadeSource source;
    final String encoded;
    final String token;
    final int seconds;
    final CrossfadeStream stream;
    CrossfadeTrack(CrossfadeSource source, AudioTrack first, String encoded, String token, int seconds) {
        super(first.getInfo()); this.source = source; this.encoded = encoded; this.token = token; this.seconds = seconds;
        stream = new CrossfadeStream(source.pcm(), first, token, seconds);
    }
    @Override public boolean isSeekable() { return false; }
    @Override public AudioSourceManager getSourceManager() { return source; }
    @Override protected AudioTrack makeShallowClone() { throw new UnsupportedOperationException("Start a fresh crossfade session instead"); }
    @Override public void process(LocalAudioTrackExecutor executor) {
        var pipeline = AudioPipelineFactory.create(executor.getProcessingContext(), new PcmFormat(2, 48_000));
        var samples = new float[2][960];
        try {
            executor.executeProcessingLoop(() -> {
                long produced = stream.produced();
                while (stream.read(samples)) {
                    long next = stream.produced();
                    if (next > produced) { pipeline.process(samples, 0, 960); produced = next; }
                }
                pipeline.flush();
            }, null);
        } finally { pipeline.close(); stream.close(); }
    }
}
