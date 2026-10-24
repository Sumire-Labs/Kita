package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import org.junit.jupiter.api.Test;

class MusicMessagesTest {
    @Test void artworkUsesComponentsV2ThumbnailAlongsideLinkedTitle() {
        var message = MusicMessages.added(new QueueAddition(1, "曲名", "https://www.youtube.com/watch?v=example",
                "https://i.ytimg.com/vi/example/hqdefault.jpg"));
        var section = json(message).get("components").get(0).get("components").get(0);
        assertEquals(9, section.get("type").asInt());
        assertEquals("[曲名](<https://www.youtube.com/watch?v=example>)をキューに追加",
                section.get("components").get(0).get("content").asText());
        var thumbnail = section.get("accessory");
        assertEquals(11, thumbnail.get("type").asInt());
        assertEquals("https://i.ytimg.com/vi/example/hqdefault.jpg", thumbnail.get("media").get("url").asText());
        assertTrue(message.embeds().toOptional().orElse(java.util.List.of()).isEmpty());
    }

    @Test void flatYouTubeSearchGetsArtworkWithoutAnExtraNetworkRequest() {
        var track = new dev.arbjerg.lavalink.protocol.v4.TrackInfo("KOR4RfYWuPs", true, "artist", 60_000,
                false, 0, "曲名", "https://www.youtube.com/watch?v=KOR4RfYWuPs", "youtube", null, null);
        assertEquals("https://i.ytimg.com/vi/KOR4RfYWuPs/hqdefault.jpg", MusicTrackDisplay.artwork(track));
    }

    @Test void addedMessageUsesLinkedTitleAndSmallPlayerHint() {
        var message = MusicMessages.added(new QueueAddition(1, "曲名", "https://www.youtube.com/watch?v=example"));
        var children = json(message).get("components").get(0).get("components");
        assertEquals("[曲名](<https://www.youtube.com/watch?v=example>)をキューに追加", children.get(0).get("content").asText());
        assertEquals("-# /player で操作できます。", children.get(1).get("content").asText());
        assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2));
        assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.SUPPRESS_NOTIFICATIONS));
    }
    @Test void malformedOrMissingSourceUrlDoesNotBreakAcknowledgement() {
        for (String url : new String[]{null, "", "javascript:alert(1)", "https://user:password@example.com/track"}) {
            var message = MusicMessages.added(new QueueAddition(1, "曲名", url));
            var text = json(message).get("components").get(0).get("components").get(0).get("content").asText();
            assertEquals("曲名をキューに追加", text);
        }
    }
    @Test void playlistReplyKeepsFirstTrackAndCount() {
        var message = MusicMessages.added(new QueueAddition(3, "First [live]", "https://example.com/track"));
        var text = json(message).get("components").get(0).get("components").get(0).get("content").asText();
        assertTrue(text.contains("First \\[live\\]"));
        assertTrue(text.endsWith("ほか2曲をキューに追加"));
    }
}
