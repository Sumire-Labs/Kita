package com.sumirelabs.kita.music;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;

public final class PlaybackQueue<T> {
    public enum Loop { OFF, TRACK, QUEUE }
    public record Snapshot<T>(T current, List<T> upcoming, List<T> history, Loop loop) {}
    private final ArrayDeque<T> upcoming = new ArrayDeque<>();
    private final ArrayDeque<T> history = new ArrayDeque<>();
    private T current;
    private Loop loop = Loop.OFF;
    private final int maximum;
    public PlaybackQueue(int maximum) { this.maximum = maximum; }

    public synchronized void add(Collection<T> tracks) {
        if (upcoming.size() + tracks.size() > maximum) throw new IllegalArgumentException("キューの上限は " + maximum + " 曲です。");
        upcoming.addAll(tracks);
    }

    public synchronized T next(boolean naturalEnd) {
        if (naturalEnd && loop == Loop.TRACK && current != null) return current;
        if (current != null) {
            if (loop == Loop.QUEUE) upcoming.addLast(current);
            history.addLast(current);
            if (history.size() > 100) history.removeFirst();
        }
        current = upcoming.pollFirst();
        return current;
    }

    public synchronized T previous() {
        if (history.isEmpty()) throw new IllegalArgumentException("前の曲はありません。");
        if (current != null && upcoming.size() >= maximum) throw new IllegalArgumentException("キューが満杯のため戻れません。");
        if (current != null) upcoming.addFirst(current);
        current = history.removeLast();
        return current;
    }

    public synchronized void clear() { upcoming.clear(); history.clear(); current = null; }
    public synchronized T current() { return current; }
    public synchronized List<T> upcoming() { return List.copyOf(upcoming); }
    public synchronized Loop loop() { return loop; }
    public synchronized void cycleLoop() { loop = Loop.values()[(loop.ordinal() + 1) % Loop.values().length]; }
    public synchronized void toggleLoop(Loop mode) { loop = loop == mode ? Loop.OFF : mode; }
    public synchronized Snapshot<T> snapshot() { return new Snapshot<>(current, List.copyOf(upcoming), List.copyOf(history), loop); }
    public synchronized void restore(Snapshot<T> snapshot) {
        current = snapshot.current(); loop = snapshot.loop();
        upcoming.clear(); upcoming.addAll(snapshot.upcoming());
        history.clear(); history.addAll(snapshot.history());
    }
}
