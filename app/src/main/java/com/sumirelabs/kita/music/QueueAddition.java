package com.sumirelabs.kita.music;

public record QueueAddition(int count, String title, String url, String artworkUrl) {
    public QueueAddition(int count, String title, String url) { this(count, title, url, null); }
}
