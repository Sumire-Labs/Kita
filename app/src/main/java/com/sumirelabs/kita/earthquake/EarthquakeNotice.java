package com.sumirelabs.kita.earthquake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public record EarthquakeNotice(String source, String sourceUrl, String key, Kind kind, long revision, long issuedMs, long occurredMs,
                               boolean cancelled, boolean test, int maxScale, String title, String place,
                               Double magnitude, Integer depth, List<String> notes, List<Area> areas) {
    public enum Kind { EARTHQUAKE, EEW_WARNING, EEW_FORECAST, TSUNAMI }
    public record Area(String prefecture, String name, int scale, String detail) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private record Fingerprints(String token, String content) {}
    private static final Cache<EarthquakeNotice, Fingerprints> HASHES = Caffeine.newBuilder().weakKeys().maximumSize(2048).build();
    public EarthquakeNotice { notes = List.copyOf(notes); areas = List.copyOf(areas); }
    public Instant issuedAt() { return Instant.ofEpochMilli(issuedMs); }
    public boolean fresh(Instant now) {
        var age = Duration.between(issuedAt(), now);
        return !test && !age.isNegative() && age.compareTo(kind == Kind.EEW_WARNING || kind == Kind.EEW_FORECAST
                ? Duration.ofMinutes(5) : Duration.ofMinutes(30)) <= 0;
    }
    public String token() { return fingerprints().token; }
    public String contentToken() { return fingerprints().content; }
    private Fingerprints fingerprints() {
        return HASHES.get(this, notice -> new Fingerprints(digest(encode()),
                digest(new EarthquakeNotice(source, sourceUrl, key, kind, 0, 0, occurredMs, cancelled, test,
                        maxScale, title, place, magnitude, depth, notes, areas).encode())));
    }
    public boolean supersedes(EarthquakeNotice old) {
        return key.equals(old.key) && !token().equals(old.token()) && revision >= old.revision
                && (revision > old.revision || (!old.cancelled || cancelled) && issuedMs >= old.issuedMs);
    }
    public String encode() {
        try { return JSON.writeValueAsString(this); } catch (Exception error) { throw new IllegalStateException("Cannot encode earthquake notice", error); }
    }
    public static EarthquakeNotice decode(String encoded) {
        try { return JSON.readValue(encoded, EarthquakeNotice.class); } catch (Exception error) { throw new IllegalStateException("Cannot decode earthquake notice", error); }
    }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
