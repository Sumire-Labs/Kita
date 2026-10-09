package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.player.Track;

public final class MusicSession {
    final PlaybackQueue<Track> queue = new PlaybackQueue<>(1000);
    int volume = 10;
    String preset = "off";
    int presetPage;
    long panelChannel;
    long panelMessage;
    long voiceChannel;
    String playbackId = "";
    String playbackError = "";
}
