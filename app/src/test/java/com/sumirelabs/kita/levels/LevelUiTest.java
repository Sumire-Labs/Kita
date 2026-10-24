package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class LevelUiTest {
    @Test void settingsSectionsAndLeaderboardsAreSilentV2WithinLimits() {
        for (String page : List.of("chat", "voice", "rewards")) {
            { var message = LevelsSettings.render(new GuildSettings(1, Map.of()), 2, page);
                assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2)); assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.SUPPRESS_NOTIFICATIONS));
                assertTrue(message.embeds().toOptional().orElse(java.util.List.of()).isEmpty());
                assertTrue(json(message).toString().split("\"type\":").length - 1 <= 40);
            }
        }
        var entries = List.of(new LevelsRepository.Entry(1, 600, 1), new LevelsRepository.Entry(2, 600, 1));
        { var board = LevelMessages.leaderboard(entries, LevelPeriod.TOTAL, 0, 2);
            assertTrue(json(board).toString().contains("Lv.2")); assertTrue(json(board).get("allowed_mentions").get("parse").isEmpty());
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
        { var response = LevelMessages.profile(first, 1);
            assertEquals(1, response.files().size()); assertTrue(response.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2));
            assertTrue(json(response).toString().contains("attachment://profile.png"));
        }
    }
}
