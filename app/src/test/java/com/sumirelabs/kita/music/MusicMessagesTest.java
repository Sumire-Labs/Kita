package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MusicMessagesTest {
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
