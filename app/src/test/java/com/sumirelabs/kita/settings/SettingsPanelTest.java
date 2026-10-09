package com.sumirelabs.kita.settings;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsPanelTest {
    @Test void eachPageFitsDiscordAndIsSilentV2() {
        for (var page : new String[]{"home", "previews", "translation", "ticket", "logshare"}) {
            var data = SettingsPanel.render(new GuildSettings(1, Map.of()), 2, page);
            assertTrue(data.isUsingComponentsV2()); assertTrue(data.isSuppressedNotifications());
            assertFalse(data.toData().hasKey("embeds"));
            var payload = data.toData().toString();
            assertTrue(payload.contains("settings:2:"));
            assertTrue(payload.split("\"type\":").length - 1 <= 40);
        }
    }
}
