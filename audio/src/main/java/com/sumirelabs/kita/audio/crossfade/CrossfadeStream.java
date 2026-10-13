package com.sumirelabs.kita.audio.crossfade;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/** Owns two bounded Lavaplayer PCM buffers. Only the mixing executor consumes them. */
final class CrossfadeStream implements AutoCloseable {
    record Segment(String token, long start, long promoted) {}
    private final AudioPlayerManager manager;
    private final int seconds;
    private PcmDeck current;
    private PcmDeck pending;
    private long fadeFrames;
    private long faded;
    private long outputFrames;
    private Segment previous;
    private Segment segment;
    private boolean closed;
    private boolean done;
    private String error = "";
    private long waitingSince;
    CrossfadeStream(AudioPlayerManager manager, AudioTrack track, String token, int seconds) {
        this.manager = manager; this.seconds = seconds; current = new PcmDeck(manager, track, token);
        segment = new Segment(token, 0, 0);
    }
    synchronized boolean prepare(AudioTrack track, String token, String expected) {
        if (closed || done || !current.token.equals(expected)) return false;
        if (pending != null) return pending.token.equals(token);
        if (track.getInfo().isStream || !track.isSeekable()) return false;
        pending = new PcmDeck(manager, track, token); return true;
    }
    synchronized void cancelPending() { if (pending != null) { pending.close(); pending = null; } fadeFrames = faded = 0; }
    synchronized void cancelBeforeFade(long milliseconds) { if (fadeFrames == 0 && audible(milliseconds) == segment) cancelPending(); }
    boolean read(float[][] output) throws InterruptedException {
        PcmDeck deck;
        synchronized (this) { if (closed || done) return false; deck = current; }
        if (!deck.read(100)) {
            synchronized (this) {
                if (waitingSince == 0) waitingSince = System.nanoTime();
                if (System.nanoTime() - waitingSince > 60_000_000_000L) deck.error = "音声の読み込みがタイムアウトしました。";
                if (deck.ended() || !deck.error.isEmpty()) { done = true; error = deck.error; return false; }
            }
            return true; // Caller retries without producing silence or advancing the timeline.
        }
        synchronized (this) {
            if (closed) return false;
            waitingSince = 0;
            long remaining = Math.max(0, current.lengthFrames() - current.frames + 960);
            long requested = pending == null ? 0 : Math.min(seconds * 48_000L, Math.min(current.lengthFrames() / 2, pending.lengthFrames() / 2));
            if (pending != null && (fadeFrames > 0 || remaining <= requested) && pending.read(fadeFrames > 0 ? 100 : 0)) {
                if (fadeFrames == 0) fadeFrames = Math.max(960, remaining);
                CrossfadeMixer.mix(current.samples, pending.samples, output, faded, fadeFrames, 960); faded += 960;
                if (faded >= fadeFrames) {
                    previous = segment; segment = new Segment(pending.token, outputFrames + 960 - faded, outputFrames + 960);
                    current.close(); current = pending; pending = null; faded = fadeFrames = 0;
                }
            } else {
                if (fadeFrames > 0) cancelPending();
                for (int channel = 0; channel < 2; channel++) System.arraycopy(current.samples[channel], 0, output[channel], 0, 960);
            }
            outputFrames += 960; return true;
        }
    }
    synchronized long produced() { return outputFrames; }
    synchronized Segment audible(long milliseconds) { return previous != null && milliseconds * 48 < segment.promoted ? previous : segment; }
    synchronized String pendingToken() { return pending == null ? "" : pending.token; }
    synchronized String nextToken(long milliseconds) { return audible(milliseconds) != segment ? segment.token : pendingToken(); }
    synchronized boolean fading(long milliseconds) {
        long frame = milliseconds * 48;
        return faded > 0 && frame >= outputFrames - faded && frame < outputFrames - faded + fadeFrames
                || previous != null && frame >= segment.start && frame < segment.promoted;
    }
    synchronized boolean done() { return done; }
    synchronized String error() { return error; }
    @Override public synchronized void close() { closed = true; current.close(); if (pending != null) pending.close(); }
}
