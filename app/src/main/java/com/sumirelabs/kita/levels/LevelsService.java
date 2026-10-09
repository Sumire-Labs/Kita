package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.time.Duration;
import java.time.Instant;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

public final class LevelsService implements AutoCloseable {
    private final SettingsRepository settings;
    private final LevelsRepository repository;
    private final RewardRoles rewards;
    private final LevelRewardQueue queue;
    private final Cache<Long, GuildSettings> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(60)).build();
    public LevelsService(SettingsRepository settings, LevelsRepository repository, WorkExecutor worker) {
        this.settings = settings; this.repository = repository; rewards = new RewardRoles(repository);
        queue = new LevelRewardQueue(worker, this::reward);
    }
    public GuildSettings settings(long guild) { return cache.get(guild, settings::get); }
    public void invalidate(long guild) { cache.invalidate(guild); }
    public LevelsRepository repository() { return repository; }
    public boolean sync(Guild guild, long user, long xp) { return rewards.sync(guild, user, xp, settings(guild.getIdLong())); }
    public LevelsRepository.Award award(Guild guild, long user, LevelsRepository.Source source, int amount, Instant now, byte[] hash,
                      long voiceToken, long channelId) {
        var setting = settings(guild.getIdLong()); var config = LevelConfig.from(setting);
        if (!config.enabled() || (source == LevelsRepository.Source.CHAT ? !config.chat() : !config.voice())) return new LevelsRepository.Award(false, 0, 0);
        var result = repository.award(guild.getIdLong(), user, source, amount, now, config.cooldown(), hash, voiceToken);
        if (!result.accepted()) return result;
        try {
            if (rewards.needsSync(guild.getIdLong(), user, result.after(), setting)
                    || config.notice().equals("levels") && LevelCurve.level(result.after()) > LevelCurve.level(result.before())) {
                queue.add(new LevelRewardQueue.Job(guild, user, result.before(), result.after(), channelId));
            }
        } catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(LevelsService.class).warn("Reward scheduling failed in guild {}", guild.getIdLong()); }
        return result;
    }
    private void reward(LevelRewardQueue.Job job) {
        var guild = job.guild(); long user = job.user();
        var setting = settings(guild.getIdLong()); var config = LevelConfig.from(setting);
        if (!config.enabled()) return;
        boolean added = rewards.sync(guild, user, job.after(), setting);
        boolean leveled = LevelCurve.level(job.after()) > LevelCurve.level(job.before());
        boolean notify = config.notice().equals("rewards") ? added : config.notice().equals("levels") && leveled;
        if (!notify) return;
        long target = config.noticeChannel() == 0 ? job.channel() : config.noticeChannel();
        if (target == 0) return;
        var channel = guild.getChannelById(GuildMessageChannel.class, target);
        try {
            if (channel != null) channel.sendMessage(Ui.text("Level Up", "<@" + user + "> · **Lv." + LevelCurve.level(job.after()) + "**"
                    + (added ? "\n報酬ロールを獲得しました。" : ""))).queue(ignored -> {}, ignored -> {});
        } catch (RuntimeException ignored) { }
    }
    @Override public void close() { queue.close(); }
}
