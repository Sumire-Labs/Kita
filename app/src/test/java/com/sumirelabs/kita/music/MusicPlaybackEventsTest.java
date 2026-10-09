package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import java.util.List;
import java.util.Map;
import kotlinx.serialization.json.JsonObject;
import org.junit.jupiter.api.Test;

class MusicPlaybackEventsTest {
    @Test void failedAudioLoadKeepsRequestedTrackAndUpcomingQueue() {
        var session = new MusicSession();
        var first = track("first", "current");
        var second = track("second", "next");
        session.queue.add(List.of(first, second));
        session.queue.next(false);
        session.playbackId = "current";
        MusicPlaybackEvents.ended(session, first, "LOAD_FAILED", () -> fail("Failed tracks must not silently advance"));
        assertSame(first, session.queue.current());
        assertEquals(List.of(second), session.queue.upcoming());
        assertFalse(session.playbackError.isBlank());
    }
    @Test void finishedTrackAdvancesButStoppedOrReplacedTracksDoNot() {
        var session = new MusicSession();
        var first = track("first", "current");
        var second = track("second", "next");
        session.queue.add(List.of(first, second)); session.queue.next(false); session.playbackId = "current";
        MusicPlaybackEvents.ended(session, first, "STOPPED", () -> fail("Manual stop is not a natural end"));
        MusicPlaybackEvents.ended(session, first, "REPLACED", () -> fail("Replacement already started another track"));
        MusicPlaybackEvents.ended(session, first, "FINISHED", () -> {
            assertFalse(Thread.holdsLock(session), "Starting the next track must not hold the state monitor during I/O");
            session.queue.next(true);
        });
        assertSame(second, session.queue.current());
    }
    @Test void staleFailureFromPreviousPlaybackDoesNotPoisonRetry() {
        var session = new MusicSession();
        session.queue.add(List.of(track("first", "new"))); session.queue.next(false); session.playbackId = "new";
        MusicPlaybackEvents.failed(session, track("first", "old"));
        assertTrue(session.playbackError.isBlank());
    }
    @Test void exceptionFollowedByEndDoesNotDiscardFailedTrack() {
        var session = new MusicSession();
        var first = track("first", "current");
        session.queue.add(List.of(first)); session.queue.next(false); session.playbackId = "current";
        MusicPlaybackEvents.failed(session, first);
        MusicPlaybackEvents.ended(session, first, "FINISHED", () -> fail("The failure must remain visible"));
        assertSame(first, session.queue.current());
    }
    private static Track track(String title, String playbackId) {
        var info = new TrackInfo(title, true, "artist", 60_000, false, 0, title, "https://example.com/" + title,
                "youtube", null, null);
        var empty = new JsonObject(Map.of());
        var track = new Track(new dev.arbjerg.lavalink.protocol.v4.Track("encoded-" + title, info, empty, empty));
        track.setUserData(Map.of("kitaPlaybackId", playbackId));
        return track;
    }
}
