package com.sumirelabs.kita.levels;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record LevelReward(int level, long roleId) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<LevelReward>> TYPE = new TypeReference<>() {};
    public LevelReward { if (level < 1 || level > 100_000 || roleId <= 0) throw new IllegalArgumentException("報酬のレベル・ロールが不正です。"); }
    public static List<LevelReward> read(GuildSettings settings) {
        try { return List.copyOf(JSON.readValue(settings.value("levels.rewards", "[]"), TYPE)); }
        catch (Exception error) { throw new IllegalArgumentException("報酬設定を読み込めません。設定を確認してください。"); }
    }
    public static String write(List<LevelReward> rewards) {
        if (rewards.size() > 25) throw new IllegalArgumentException("報酬は25件までです。");
        try { return JSON.writeValueAsString(rewards); }
        catch (Exception error) { throw new IllegalStateException("Cannot encode rewards", error); }
    }
    public static Set<Long> desired(List<LevelReward> rewards, int level, boolean stack) {
        int highest = rewards.stream().filter(r -> r.level <= level).mapToInt(LevelReward::level).max().orElse(0);
        return rewards.stream().filter(r -> r.level <= level && (stack || r.level == highest))
                .map(LevelReward::roleId).collect(Collectors.toUnmodifiableSet());
    }
}
