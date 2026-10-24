package com.sumirelabs.kita.earthquake;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EarthquakeUiTest {
    @Test void alertsAreComponentsV2AndNormalByDefaultWithOnlyAnExplicitRoleMention() {
        var payload = json(EarthquakeMessages.render(Notices.warning(1, false), false, 123));
        assertEquals(32768, payload.get("flags").asInt()); assertTrue(payload.path("embeds").isMissingNode());
        assertTrue(payload.path("allowed_mentions").path("parse").isArray());
        assertTrue(payload.path("allowed_mentions").path("parse").isEmpty());
        assertEquals("123", payload.path("allowed_mentions").path("roles").get(0).asText());
        assertFalse(payload.path("allowed_mentions").path("replied_user").asBoolean());
        assertEquals(36864, json(EarthquakeMessages.render(Notices.warning(1, false), true, 0)).path("flags").asInt());
    }
    @Test void allSettingsPagesFitComponentsLimitsAndProvideSafePrivatePreview() {
        for (var page : new String[]{"general", "filters", "delivery"}) {
            var payload = json(EarthquakeSettings.render(new GuildSettings(1, Map.of()), 2, page, "受信接続中"));
            assertEquals(36864, payload.path("flags").asInt()); assertTrue(payload.toString().contains("earthquake-settings:2:"));
            assertTrue(payload.toString().split("\"type\":").length - 1 <= 40);
        }
        assertTrue(json(EarthquakeMessages.preview()).toString().contains("実際の地震情報ではありません"));
    }
}
