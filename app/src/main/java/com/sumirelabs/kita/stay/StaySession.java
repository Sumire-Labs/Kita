package com.sumirelabs.kita.stay;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class StaySession {
    private final java.util.concurrent.locks.ReentrantLock operations = new java.util.concurrent.locks.ReentrantLock();
    record View(String token, long voiceChannel, Instant until, long panelChannel, long panelMessage) {
        boolean active() { return voiceChannel != 0; }
    }
    private String token = UUID.randomUUID().toString();
    private long voiceChannel;
    private Instant until;
    private long panelChannel;
    private long panelMessage;

    void operate(Runnable action) {
        operations.lock();
        try { action.run(); }
        finally { operations.unlock(); }
    }
    synchronized View view() { return new View(token, voiceChannel, until, panelChannel, panelMessage); }
    synchronized void start(long channel) { token = UUID.randomUUID().toString(); voiceChannel = channel; until = null; }
    synchronized void move(long channel) { voiceChannel = channel; }
    synchronized void stop() { voiceChannel = 0; until = null; token = UUID.randomUUID().toString(); }
    synchronized void limit(Instant now, Duration duration) { until = duration == null ? null : now.plus(duration); }
    synchronized boolean due(Instant now) { return voiceChannel != 0 && until != null && !now.isBefore(until); }
    synchronized void panel(long channel, long message) { panelChannel = channel; panelMessage = message; }
    synchronized void require(String expected) {
        if (voiceChannel == 0 || !token.equals(expected)) {
            throw new IllegalArgumentException("この常駐パネルは終了済みです。/stay で常駐を開始してください。");
        }
    }
    static Duration duration(String hours, String minutes) {
        try {
            if (!hours.matches("[0-9]{1,6}") || !minutes.matches("[0-9]{1,2}")) throw new NumberFormatException();
            int h = Integer.parseInt(hours), m = Integer.parseInt(minutes);
            if (m > 59 || h == 0 && m == 0) throw new NumberFormatException();
            return Duration.ofMinutes(h * 60L + m);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("時間は0〜999999、分は0〜59の整数で、合計1分以上にしてください。");
        }
    }
}
