package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.SerialActions;
import com.sumirelabs.kita.settings.GuildSettings;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class RewardRoles implements AutoCloseable {
    private record Key(long guild, long user, int level, String configuration, boolean stack) {}
    private final LevelsRepository repository;
    private final Map<Long, SerialActions> operations = new ConcurrentHashMap<>();
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
    Mono<Boolean> sync(Guild guild, long user, long xp, GuildSettings settings) {
        return Mono.defer(() -> {
            if (!needsSync(guild.getId().asLong(), user, xp, settings)) return Mono.just(false);
            return operations.computeIfAbsent(guild.getId().asLong(), ignored -> new SerialActions())
                    .submit(() -> repository.profile(guild.getId().asLong(), user).flatMap(profile -> apply(guild, user, Math.max(xp, profile.xp()), settings)));
        });
    }
    private Mono<Boolean> apply(Guild guild, long user, long xp, GuildSettings settings) {
        long id = guild.getId().asLong();
        var config = LevelConfig.from(settings);
        var key = new Key(id, user, LevelCurve.level(xp), settings.value("levels.rewards", "[]"), config.stackRewards());
        if (synced.getIfPresent(key) != null) return Mono.just(false);
        var desired = LevelReward.desired(LevelReward.read(settings), key.level, config.stackRewards());
        return repository.grants(id, user).flatMap(grants -> Access.member(guild.getClient(), id, user).flatMap(member ->
                Flux.fromIterable(desired).concatMap(roleId -> {
                    if (member.getRoleIds().contains(Access.id(roleId))) return Mono.just(false);
                    return guild.getRoleById(Access.id(roleId)).flatMap(role -> require(guild, role)
                            .then(member.addRole(role.getId())).then(repository.granted(id, user, roleId)).thenReturn(true)).defaultIfEmpty(false);
                }).collectList().flatMap(added -> Flux.fromIterable(grants).filter(roleId -> !desired.contains(roleId))
                        .concatMap(roleId -> guild.getRoleById(Access.id(roleId)).flatMap(role -> member.getRoleIds().contains(role.getId())
                                        ? require(guild, role).then(member.removeRole(role.getId())) : Mono.empty())
                                .then(repository.revoked(id, user, roleId)))
                        .then(Mono.just(added.contains(true)))))).doOnSuccess(ignored -> synced.put(key, true));
    }
    static Mono<Void> require(Guild guild, Role role) {
        return Access.member(guild.getClient(), guild.getId().asLong(), guild.getClient().getSelfId().asLong()).flatMap(self -> Access.role(self, role));
    }
    @Override public void close() { operations.values().forEach(SerialActions::close); }
}
