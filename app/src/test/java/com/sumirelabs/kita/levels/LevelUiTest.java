package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class LevelUiTest {
    @Test void settingsSectionsAndLeaderboardsAreSilentV2WithinLimits() {
        for (String page : List.of("chat", "voice", "rewards")) {
            try (var message = LevelsSettings.render(new GuildSettings(1, Map.of()), 2, page)) {
                assertTrue(message.isUsingComponentsV2()); assertTrue(message.isSuppressedNotifications());
                assertTrue(message.getEmbeds().isEmpty());
                assertTrue(message.toData().toString().split("\"type\":").length - 1 <= 40);
            }
        }
        var entries = List.of(new LevelsRepository.Entry(1, 600, 1), new LevelsRepository.Entry(2, 600, 1));
        try (var board = LevelMessages.leaderboard(entries, LevelPeriod.TOTAL, 0, 2)) {
            assertTrue(board.toData().toString().contains("Lv.2")); assertTrue(board.getAllowedMentions().isEmpty());
        }
    }
    @Test void nativeRendererProducesDeterministicDistinctProfilesWithJapaneseText() throws Exception {
        var renderer = new ProfileCardRenderer();
        var card = new ProfileCardRenderer.Card(1, "すみれ / Sumire 🌸", 16_000, 8);
        byte[] first = renderer.render(card, new byte[0]);
        assertArrayEquals(first, renderer.render(card, new byte[0]));
        byte[] other = renderer.render(new ProfileCardRenderer.Card(2, card.name(), card.xp(), card.rank()), new byte[0]);
        assertFalse(java.util.Arrays.equals(first, other));
        var image = ImageIO.read(new ByteArrayInputStream(first));
        assertEquals(ProfileCardRenderer.WIDTH, image.getWidth()); assertEquals(ProfileCardRenderer.HEIGHT, image.getHeight());
        try (var response = LevelMessages.profile(first, 1)) {
            assertEquals(1, response.getFiles().size()); assertTrue(response.isUsingComponentsV2());
            assertTrue(response.toData().toString().contains("attachment://profile.png"));
        }
    }
}
