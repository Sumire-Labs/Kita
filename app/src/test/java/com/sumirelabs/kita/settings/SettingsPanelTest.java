package com.sumirelabs.kita.settings;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsPanelTest {
    @Test void eachPageFitsDiscordAndIsSilentV2() {
        for (var page : new String[]{"home", "previews", "translation", "ticket", "logshare", "levels"}) {
            var data = SettingsPanel.render(new GuildSettings(1, Map.of()), 2, page);
            assertTrue(data.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2)); assertTrue(data.flags().get().contains(discord4j.core.object.entity.Message.Flag.SUPPRESS_NOTIFICATIONS));
            assertFalse(json(data).has("embeds"));
            var payload = json(data).toString();
            assertTrue(payload.contains("settings:2:"));
            assertTrue(payload.split("\"type\":").length - 1 <= 40);
        }
    }
}
