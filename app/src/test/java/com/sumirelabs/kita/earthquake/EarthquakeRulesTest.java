package com.sumirelabs.kita.earthquake;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.settings.GuildSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EarthquakeRulesTest {
    @Test void defaultsAreDisabledNationwideAndNormalNotifications() {
        var config = EarthquakeConfig.from(new GuildSettings(1, Map.of()));
        assertFalse(config.enabled()); assertFalse(config.silent()); assertTrue(config.channels().isEmpty());
        assertTrue(config.regions().isEmpty()); assertEquals("new", config.updates());
    }
    @Test void regionFilterUsesLocalObservedIntensityAndSeparateTsunamiKeywords() throws Exception {
        var config = EarthquakeConfig.from(new GuildSettings(1, Map.of("earthquake.enabled", "true", "earthquake.minimum", "30",
                "earthquake.regions", "千葉県", "earthquake.tsunamiRegions", "伊豆諸島")));
        var observed = new P2pNoticeParser().parse(Notices.payload()).orElseThrow();
        assertFalse(config.matches(observed), "Tokyo's intensity must not satisfy Chiba's threshold");
        var tokyo = EarthquakeConfig.from(new GuildSettings(1, Map.of("earthquake.enabled", "true", "earthquake.minimum", "30", "earthquake.regions", "東京都")));
        assertTrue(tokyo.matches(observed)); assertTrue(tokyo.matches(Notices.warning(1, false)));
        assertFalse(tokyo.matches(Notices.warning(1, true)), "Cancellation requires an existing delivery");
        assertEquals("東京都,千葉県", EarthquakeSettingsListener.clean(" 東京都、千葉県\n東京都"));
    }
    @Test void noticeIdentityRoundTripsAndProtectsOrderEvenAtEqualCancellationSerial() {
        var first = Notices.warning(2, false); var cancelled = Notices.warning(2, true);
        assertEquals(first, EarthquakeNotice.decode(first.encode())); assertFalse(first.supersedes(first));
        assertTrue(cancelled.supersedes(first)); assertFalse(first.supersedes(cancelled));
        assertFalse(Notices.warning(1, false).supersedes(first)); assertTrue(Notices.warning(3, false).supersedes(first));
        assertEquals(first.contentToken(), Notices.warning(3, false).contentToken());
        assertTrue(first.fresh(first.issuedAt().plusSeconds(30)));
        assertFalse(first.fresh(first.issuedAt().plus(Duration.ofMinutes(6))));
        assertFalse(first.fresh(Instant.EPOCH));
    }
}