package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sumirelabs.kita.common.CrossfadeState;
import com.sumirelabs.kita.settings.GuildSettings;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CrossfadePlaybackTest {
    private Track track(String id, long length) {
        var track = mock(Track.class); var info = mock(TrackInfo.class);
        when(track.getInfo()).thenReturn(info); when(track.getEncoded()).thenReturn(id);
        when(info.getLength()).thenReturn(length); when(info.isSeekable()).thenReturn(true); return track;
    }
    @Test void preparingDoesNotConsumeQueueAndPromotionConsumesExactlyOnce() {
        var api = mock(CrossfadeApi.class); var session = new MusicSession();
        var a = track("A", 30_000); var b = track("B", 100_000);
        session.queue.add(List.of(a, b)); session.queue.next(false);
        session.crossfadeActive = true; session.playbackId = "engine"; session.crossfadeSeconds = 5;
        var state = new AtomicReference<>(new CrossfadeState("engine", "engine", 10_000, "", false, false, ""));
        when(api.request(eq(1L), anyString(), eq("GET"), isNull())).thenAnswer(ignored -> Mono.just(state.get()));
        when(api.request(eq(1L), anyString(), eq("PUT"), anyMap())).thenAnswer(ignored -> Mono.just(state.get()));
        try (var playback = new CrossfadePlayback(api, Map.of(1L, session), null)) {
            playback.sync(1, session).block();
            assertSame(a, session.queue.current()); assertEquals(List.of(b), session.queue.upcoming());
            state.set(new CrossfadeState("engine", session.crossfadeNextToken, 5000, "", false, false, ""));
            playback.sync(1, session).block(); playback.sync(1, session).block();
            assertSame(b, session.queue.current()); assertTrue(session.queue.upcoming().isEmpty());
            assertSame(a, session.queue.previous());
        }
    }
    @Test void staleGenerationAndNetworkFailureCannotAdvanceQueue() {
        var api = mock(CrossfadeApi.class); var session = new MusicSession(); var a = track("A", 100_000);
        session.queue.add(List.of(a)); session.queue.next(false); session.crossfadeActive = true; session.playbackId = "new";
        when(api.request(eq(1L), anyString(), eq("GET"), isNull())).thenReturn(Mono.just(new CrossfadeState("old", "old", 0, "", false, true, "")));
        try (var playback = new CrossfadePlayback(api, Map.of(), null)) {
            playback.sync(1, session).block(); assertSame(a, session.queue.current());
            when(api.request(eq(1L), anyString(), eq("GET"), isNull())).thenReturn(Mono.error(new IllegalStateException("offline")));
            for (int attempt = 0; attempt < 3; attempt++) playback.sync(1, session).block();
            assertSame(a, session.queue.current()); assertFalse(session.playbackError.isEmpty());
        }
    }
    @Test void timedOutPreparationStillRecognizesAnAcceptedTransition() {
        var api = mock(CrossfadeApi.class); var session = new MusicSession();
        var a = track("A", 30_000); var b = track("B", 100_000); session.queue.add(List.of(a, b)); session.queue.next(false);
        session.crossfadeActive = true; session.playbackId = "engine"; session.crossfadeSeconds = 5;
        var state = new AtomicReference<>(new CrossfadeState("engine", "engine", 10_000, "", false, false, ""));
        when(api.request(eq(1L), anyString(), eq("GET"), isNull())).thenAnswer(ignored -> Mono.just(state.get()));
        when(api.request(eq(1L), anyString(), eq("PUT"), anyMap())).thenReturn(Mono.error(new IllegalStateException("response lost")));
        try (var playback = new CrossfadePlayback(api, Map.of(), null)) {
            playback.sync(1, session).block(); assertSame(a, session.queue.current());
            assertFalse(session.crossfadeNextToken.isBlank());
            state.set(new CrossfadeState("engine", session.crossfadeNextToken, 5000, "", false, false, ""));
            playback.sync(1, session).block(); assertSame(b, session.queue.current()); assertTrue(session.queue.upcoming().isEmpty());
        }
    }
    @Test void settingsAreBoundedAndDisabledByDefault() {
        assertEquals(0, MusicSettings.seconds(new GuildSettings(1, Map.of())));
        assertEquals(5, MusicSettings.seconds(new GuildSettings(1, Map.of("music.crossfade", "5"))));
        assertEquals(0, MusicSettings.seconds(new GuildSettings(1, Map.of("music.crossfade", "13"))));
        var json = com.sumirelabs.kita.discord.Payloads.json(com.sumirelabs.kita.settings.SettingsPanel.render(new GuildSettings(1, Map.of()), 2, "music"));
        assertEquals(36864, json.path("flags").asInt()); assertFalse(json.has("embeds"));
        assertTrue(json.toString().contains("settings:2:music.crossfade"));
    }
}
