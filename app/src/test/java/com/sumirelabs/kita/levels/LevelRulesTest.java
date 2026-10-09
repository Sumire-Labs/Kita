package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sumirelabs.kita.settings.GuildSettings;
import org.junit.jupiter.api.Test;

class LevelRulesTest {
    @Test void thresholdsAndProgressAreConsistentIncludingLargeXp() {
        assertEquals(0, LevelCurve.level(199)); assertEquals(1, LevelCurve.level(200));
        assertEquals(2, LevelCurve.level(600)); assertEquals(400, LevelCurve.remaining(200));
        assertEquals(0.5, LevelCurve.progress(400), 0.00001);
        assertEquals(1, LevelCurve.progress(LevelCurve.MAX_XP));
        assertEquals(LevelCurve.MAX_LEVEL, LevelCurve.level(LevelCurve.MAX_XP));
        for (int level : new int[]{0, 1, 12, 100, 10_000, 99_999}) assertEquals(level, LevelCurve.level(LevelCurve.threshold(level)));
    }
    @Test void periodsUseJapanTimeAndMondayWeekBoundaries() {
        var before = Instant.parse("2026-10-04T14:59:59Z");
        var after = before.plusSeconds(1);
        assertEquals(LocalDate.of(2026, 9, 28), LevelPeriod.WEEK.since(before));
        assertEquals(LocalDate.of(2026, 10, 5), LevelPeriod.WEEK.since(after));
        assertEquals(LocalDate.of(2026, 10, 1), LevelPeriod.MONTH.since(after));
    }
    @Test void defaultsAndExclusionsMatchAcceptedPlan() {
        var config = LevelConfig.from(new GuildSettings(1, Map.of()));
        assertFalse(config.enabled()); assertTrue(config.chat()); assertTrue(config.voice());
        assertTrue(config.mutedVoice()); assertTrue(config.stackRewards());
        assertEquals(20, config.chatXp()); assertEquals(60, config.cooldown()); assertEquals(5, config.voiceXp());
        var exclusions = LevelConfig.from(new GuildSettings(1, Map.of("levels.channels", "10,20", "levels.roles", "30")));
        assertFalse(exclusions.channel(10, 0)); assertFalse(exclusions.channel(99, 20)); assertTrue(exclusions.channel(99, 0));
        assertFalse(exclusions.member(Set.of("30"))); assertTrue(exclusions.member(Set.of("40")));
    }
    @Test void rewardsSupportStackingMultipleRolesAndHighestOnly() {
        var list = List.of(new LevelReward(5, 50), new LevelReward(10, 100), new LevelReward(10, 101));
        assertEquals(Set.of(50L, 100L, 101L), LevelReward.desired(list, 10, true));
        assertEquals(Set.of(100L, 101L), LevelReward.desired(list, 10, false));
        assertTrue(LevelReward.desired(list, 4, true).isEmpty());
        assertEquals(list, LevelReward.read(new GuildSettings(1, Map.of("levels.rewards", LevelReward.write(list)))));
        assertThrows(IllegalArgumentException.class, () -> new LevelReward(0, 1));
        assertThrows(IllegalArgumentException.class, () -> LevelSettingsModals.integer("0", 1, 10000));
    }
}
