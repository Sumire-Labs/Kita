package com.sumirelabs.kita.audio.crossfade;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class PcmDeck implements AutoCloseable {
    final AudioTrack track;
    final String token;
    final float[][] samples = new float[2][960];
    private final AudioPlayer player;
    private final long lengthFrames;
    long frames;
    volatile String error = "";
    PcmDeck(AudioPlayerManager manager, AudioTrack track, String token) {
        this(manager, track, token, 1000);
    }
    PcmDeck(AudioPlayerManager manager, AudioTrack track, String token, int bufferMillis) {
        this.track = track; this.token = token;
        lengthFrames = Math.multiplyExact(track.getInfo().length, 48);
        player = manager.createPlayer();
        player.setFrameBufferDuration(bufferMillis);
        player.addListener(new AudioEventAdapter() {
            @Override public void onTrackException(AudioPlayer source, AudioTrack failed, FriendlyException failure) { error = "音声を取得できませんでした。"; }
        });
        try { player.playTrack(track); } catch (RuntimeException failure) { player.destroy(); throw failure; }
    }
    boolean read(long timeout) throws InterruptedException {
        AudioFrame frame;
        try { frame = timeout == 0 ? player.provide() : player.provide(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException unavailable) { return false; }
        if (frame == null || frame.isTerminator()) return false;
        var bytes = ByteBuffer.wrap(frame.getData()).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.remaining() != 3840) throw new IllegalStateException("Expected a 20 ms stereo PCM frame");
        for (int index = 0; index < 960; index++) for (int channel = 0; channel < 2; channel++) samples[channel][index] = bytes.getShort() / 32768f;
        frames += 960; return true;
    }
    boolean ended() { return player.getPlayingTrack() == null; }
    long lengthFrames() { return lengthFrames; }
    @Override public void close() { player.destroy(); }
}
