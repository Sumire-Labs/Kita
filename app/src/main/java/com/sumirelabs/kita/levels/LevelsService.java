package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class LevelsService implements AutoCloseable {
    private final SettingsRepository settings;
    private final LevelsRepository repository;
    private final RewardRoles rewards;
    private final LevelRewardQueue queue;
    private final Cache<Long, Mono<GuildSettings>> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(60)).build();
    public LevelsService(SettingsRepository settings, LevelsRepository repository) {
        this.settings = settings; this.repository = repository; rewards = new RewardRoles(repository); queue = new LevelRewardQueue(this::reward);
    }
    public Mono<GuildSettings> settings(long guild) {
        return Mono.defer(() -> cache.get(guild, id -> settings.get(id).cache(Duration.ofSeconds(60))));
    }
    public void invalidate(long guild) { cache.invalidate(guild); }
    public LevelsRepository repository() { return repository; }
    public Mono<Boolean> sync(Guild guild, long user, long xp) {
        return settings(guild.getId().asLong()).flatMap(config -> rewards.sync(guild, user, xp, config));
    }
    public Mono<LevelsRepository.Award> award(Guild guild, long user, LevelsRepository.Source source, int amount, Instant now, byte[] hash,
                                             long voiceToken, long channel) {
        return settings(guild.getId().asLong()).flatMap(setting -> {
            var config = LevelConfig.from(setting);
            if (!config.enabled() || (source == LevelsRepository.Source.CHAT ? !config.chat() : !config.voice())) {
                return Mono.just(new LevelsRepository.Award(false, 0, 0));
            }
            return repository.award(guild.getId().asLong(), user, source, amount, now, config.cooldown(), hash, voiceToken).doOnNext(result -> {
                if (result.accepted() && (rewards.needsSync(guild.getId().asLong(), user, result.after(), setting)
                        || config.notice().equals("levels") && LevelCurve.level(result.after()) > LevelCurve.level(result.before()))) {
                    queue.add(new LevelRewardQueue.Job(guild, user, result.before(), result.after(), channel));
                }
            });
        });
    }
    private Mono<Void> reward(LevelRewardQueue.Job job) {
        return settings(job.guild().getId().asLong()).flatMap(setting -> {
            var config = LevelConfig.from(setting);
            if (!config.enabled()) return Mono.empty();
            return rewards.sync(job.guild(), job.user(), job.after(), setting).flatMap(added -> {
                boolean notify = config.notice().equals("rewards") ? added : config.notice().equals("levels")
                        && LevelCurve.level(job.after()) > LevelCurve.level(job.before());
                long target = config.noticeChannel() == 0 ? job.channel() : config.noticeChannel();
                if (!notify || target == 0) return Mono.empty();
                return job.guild().getClient().getChannelById(Access.id(target)).ofType(GuildMessageChannel.class)
                        .flatMap(channel -> channel.createMessage(Ui.text("Level Up", "<@" + job.user() + "> · **Lv." + LevelCurve.level(job.after())
                                + "**" + (added ? "\n報酬ロールを獲得しました。" : "")))).then();
            });
        });
    }
    @Override public void close() { queue.close(); rewards.close(); }
}
