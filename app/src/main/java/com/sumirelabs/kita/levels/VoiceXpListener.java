package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.WorkExecutor;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class VoiceXpListener extends ListenerAdapter implements AutoCloseable {
    private final Map<Long, VoiceTracker> trackers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    public VoiceXpListener(LevelsService levels, Supplier<List<Guild>> guilds, WorkExecutor worker) {
        timer.scheduleWithFixedDelay(() -> {
            if (!running.compareAndSet(false, true)) return;
            if (!worker.submit(() -> {
                try {
                    var all = guilds.get(); var ids = all.stream().map(Guild::getIdLong).collect(Collectors.toSet());
                    trackers.keySet().retainAll(ids);
                    for (var guild : all) {
                        try { tick(levels, guild); }
                        catch (Exception error) { LoggerFactory.getLogger(VoiceXpListener.class).warn("Voice XP failed in guild {}", guild.getIdLong()); }
                    }
                } finally { running.set(false); }
            })) running.set(false);
        }, 15, 15, TimeUnit.SECONDS);
    }
    private void tick(LevelsService levels, Guild guild) {
        var tracker = trackers.computeIfAbsent(guild.getIdLong(), ignored -> new VoiceTracker());
        var config = LevelConfig.from(levels.settings(guild.getIdLong()));
        if (!config.enabled() || !config.voice() || guild.getJDA().getStatus() != JDA.Status.CONNECTED) { tracker.clear(); return; }
        var eligible = new HashMap<Long, Long>();
        for (var channel : guild.getVoiceChannels()) {
            if (channel.equals(guild.getAfkChannel()) || !config.channel(channel.getIdLong(), 0)) continue;
            var humans = channel.getMembers().stream().filter(member -> !member.getUser().isBot() && member.getVoiceState() != null
                    && !member.getVoiceState().isDeafened()).toList();
            if (humans.size() < 2) continue;
            for (var member : humans) {
                if ((!config.mutedVoice() && member.getVoiceState().isMuted()) || !config.member(member.getRoles().stream()
                        .map(role -> role.getId()).collect(Collectors.toSet()))) continue;
                eligible.put(member.getIdLong(), channel.getIdLong());
            }
        }
        var now = Instant.now();
        for (var credit : tracker.update(eligible, now)) {
            try {
                levels.award(guild, credit.userId(), LevelsRepository.Source.VOICE,
                        config.voiceXp() * credit.minutes(), Instant.ofEpochSecond(credit.token() * 60), null, credit.token(), 0);
                tracker.acknowledge(credit);
            } catch (Exception error) { LoggerFactory.getLogger(VoiceXpListener.class).warn("Voice XP will retry in guild {}", guild.getIdLong()); }
        }
    }
    @Override public void onGenericGuildVoice(GenericGuildVoiceEvent event) {
        var tracker = trackers.get(event.getGuild().getIdLong());
        if (tracker == null) return;
        var state = event.getMember().getVoiceState();
        if (event instanceof GuildVoiceUpdateEvent || state == null || state.isDeafened()) tracker.invalidate(event.getMember().getIdLong());
    }
    @Override public void close() { timer.shutdownNow(); }
}
