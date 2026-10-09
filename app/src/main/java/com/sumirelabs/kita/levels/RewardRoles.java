package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.settings.GuildSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.LoggerFactory;

final class RewardRoles {
    private record Key(long guild, long user, int level, String configuration, boolean stack) {}
    private final LevelsRepository repository;
    private final Object[] locks = java.util.stream.IntStream.range(0, 256).mapToObj(ignored -> new Object()).toArray();
    private final Cache<Key, Boolean> synced = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofMinutes(15)).build();
    RewardRoles(LevelsRepository repository) { this.repository = repository; }
    boolean needsSync(long guild, long user, long xp, GuildSettings settings) {
        var rewards = LevelReward.read(settings); var config = LevelConfig.from(settings);
        int minimum = rewards.stream().mapToInt(LevelReward::level).min().orElse(100_001);
        try { minimum = Math.min(minimum, Integer.parseInt(settings.value("levels.minimumReward", "100001"))); }
        catch (NumberFormatException ignored) { }
        return LevelCurve.level(xp) >= minimum && synced.getIfPresent(new Key(guild, user, LevelCurve.level(xp),
                settings.value("levels.rewards", "[]"), config.stackRewards())) == null;
    }
    boolean sync(Guild guild, long user, long xp, GuildSettings settings) {
        if (!needsSync(guild.getIdLong(), user, xp, settings)) return false;
        synchronized (locks[Math.floorMod(java.util.Objects.hash(guild.getIdLong(), user), locks.length)]) {
            return apply(guild, user, Math.max(xp, repository.profile(guild.getIdLong(), user).xp()), settings);
        }
    }
    private boolean apply(Guild guild, long user, long xp, GuildSettings settings) {
        try {
        var rewards = LevelReward.read(settings);
        var config = LevelConfig.from(settings);
        var key = new Key(guild.getIdLong(), user, LevelCurve.level(xp), settings.value("levels.rewards", "[]"), config.stackRewards());
        if (synced.getIfPresent(key) != null) return false;
        var grants = repository.grants(guild.getIdLong(), user);
        if (rewards.isEmpty() && grants.isEmpty()) { synced.put(key, true); return false; }
            var member = guild.retrieveMemberById(user).complete();
            Set<Long> desired = LevelReward.desired(rewards, key.level, config.stackRewards());
            var add = new ArrayList<Role>(); var remove = new ArrayList<Role>();
            for (long id : desired) {
                var role = guild.getRoleById(id);
                if (role == null) continue;
                if (!member.getRoles().contains(role)) { require(guild, role); add.add(role); }
            }
            for (long id : grants) {
                if (desired.contains(id)) continue;
                var role = guild.getRoleById(id);
                if (role != null && member.getRoles().contains(role)) { require(guild, role); remove.add(role); }
            }
            if (!add.isEmpty() || !remove.isEmpty()) guild.modifyMemberRoles(member, add, remove).complete();
            for (var role : add) repository.granted(guild.getIdLong(), user, role.getIdLong());
            for (long id : grants) if (!desired.contains(id)) repository.revoked(guild.getIdLong(), user, id);
            synced.put(key, true); return !add.isEmpty();
        } catch (Exception error) {
            LoggerFactory.getLogger(RewardRoles.class).warn("Level reward sync failed in guild {} for user {}: {}",
                    guild.getIdLong(), user, error.getClass().getSimpleName());
            return false;
        }
    }
    static void require(Guild guild, Role role) {
        if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)
                || !guild.getSelfMember().canInteract(role)) throw new IllegalArgumentException("Botが操作できる通常のロールを選択してください。");
    }
}
