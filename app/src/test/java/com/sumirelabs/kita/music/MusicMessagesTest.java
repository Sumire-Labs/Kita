package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MusicMessagesTest {
    @Test void artworkUsesComponentsV2ThumbnailAlongsideLinkedTitle() {
        var message = MusicMessages.added(new QueueAddition(1, "曲名", "https://www.youtube.com/watch?v=example",
                "https://i.ytimg.com/vi/example/hqdefault.jpg"));
        var section = message.toData().getArray("components").getObject(0).getArray("components").getObject(0);
        assertEquals(9, section.getInt("type"));
        assertEquals("[曲名](<https://www.youtube.com/watch?v=example>)をキューに追加",
                section.getArray("components").getObject(0).getString("content"));
        var thumbnail = section.getObject("accessory");
        assertEquals(11, thumbnail.getInt("type"));
        assertEquals("https://i.ytimg.com/vi/example/hqdefault.jpg", thumbnail.getObject("media").getString("url"));
        assertTrue(message.getEmbeds().isEmpty());
    }

    @Test void flatYouTubeSearchGetsArtworkWithoutAnExtraNetworkRequest() {
        var track = new dev.arbjerg.lavalink.protocol.v4.TrackInfo("KOR4RfYWuPs", true, "artist", 60_000,
                false, 0, "曲名", "https://www.youtube.com/watch?v=KOR4RfYWuPs", "youtube", null, null);
        assertEquals("https://i.ytimg.com/vi/KOR4RfYWuPs/hqdefault.jpg", MusicTrackDisplay.artwork(track));
    }

    @Test void addedMessageUsesLinkedTitleAndSmallPlayerHint() {
        var message = MusicMessages.added(new QueueAddition(1, "曲名", "https://www.youtube.com/watch?v=example"));
        var children = message.toData().getArray("components").getObject(0).getArray("components");
        assertEquals("[曲名](<https://www.youtube.com/watch?v=example>)をキューに追加", children.getObject(0).getString("content"));
        assertEquals("-# /player で操作できます。", children.getObject(1).getString("content"));
        assertTrue(message.isUsingComponentsV2());
        assertTrue(message.isSuppressedNotifications());
    }
    @Test void malformedOrMissingSourceUrlDoesNotBreakAcknowledgement() {
        for (String url : new String[]{null, "", "javascript:alert(1)", "https://user:password@example.com/track"}) {
            var message = MusicMessages.added(new QueueAddition(1, "曲名", url));
            var text = message.toData().getArray("components").getObject(0).getArray("components").getObject(0).getString("content");
            assertEquals("曲名をキューに追加", text);
        }
    }
    @Test void playlistReplyKeepsFirstTrackAndCount() {
        var message = MusicMessages.added(new QueueAddition(3, "First [live]", "https://example.com/track"));
        var text = message.toData().getArray("components").getObject(0).getArray("components").getObject(0).getString("content");
        assertTrue(text.contains("First \\[live\\]"));
        assertTrue(text.endsWith("ほか2曲をキューに追加"));
    }
}
