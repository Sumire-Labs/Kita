package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.player.Track;

public final class MusicSession {
    private final java.util.concurrent.locks.ReentrantLock operations = new java.util.concurrent.locks.ReentrantLock(true);
    final PlaybackQueue<Track> queue = new PlaybackQueue<>(1000);
    int volume = 10;
    String preset = "off";
    int presetPage;
    private long panelChannel;
    private long panelMessage;
    private long refreshingPanel;
    long voiceChannel;
    boolean staying;
    String playbackId = "";
    String playbackError = "";

    record Panel(long channelId, long messageId) {}
    void operate(Runnable action) {
        operations.lock();
        try { action.run(); }
        finally { operations.unlock(); }
    }
    synchronized void stopped() { queue.clear(); if (!staying) voiceChannel = 0; }
    synchronized Panel replacePanel(long channelId, long messageId) {
        var previous = new Panel(panelChannel, panelMessage);
        panelChannel = channelId; panelMessage = messageId;
        return previous;
    }
    synchronized boolean isCurrentPanel(long messageId) { return messageId != 0 && panelMessage == messageId; }
    synchronized Panel beginPanelRefresh() {
        if (panelMessage == 0 || refreshingPanel == panelMessage) return null;
        refreshingPanel = panelMessage;
        return new Panel(panelChannel, panelMessage);
    }
    synchronized void finishPanelRefresh(long messageId, boolean deleted) {
        if (refreshingPanel == messageId) refreshingPanel = 0;
        if (deleted && panelMessage == messageId) panelMessage = 0;
    }
}
