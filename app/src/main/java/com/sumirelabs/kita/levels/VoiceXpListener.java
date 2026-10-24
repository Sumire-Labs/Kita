package com.sumirelabs.kita.levels;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.retriever.EntityRetrievalStrategy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class VoiceXpListener implements AutoCloseable {
    private final Map<Long, VoiceTracker> trackers = new ConcurrentHashMap<>();
    private final Disposable timer;
    public VoiceXpListener(LevelsService levels, GatewayDiscordClient client) {
        timer = Flux.interval(Duration.ofSeconds(15)).onBackpressureDrop().concatMap(tick ->
                client.getGuilds().flatMap(guild -> tick(levels, guild).onErrorResume(error -> Mono.empty()), 16)).subscribe();
    }
    private Mono<Void> tick(LevelsService levels, Guild guild) {
        long id = guild.getId().asLong();
        var tracker = trackers.computeIfAbsent(id, ignored -> new VoiceTracker());
        return levels.settings(id).map(LevelConfig::from).flatMap(config -> {
            if (!config.enabled() || !config.voice()) { tracker.clear(); return Mono.empty(); }
            return guild.getVoiceStates().filter(state -> state.getChannelId().isPresent() && !state.isDeaf() && !state.isSelfDeaf()
                            && !state.getChannelId().equals(guild.getAfkChannelId())
                            && config.channel(state.getChannelId().orElseThrow().asLong(), 0))
                    .flatMap(state -> state.getMember(EntityRetrievalStrategy.STORE).filter(member -> !member.isBot())
                            .map(member -> Map.entry(state, member)), 16).collectList().flatMap(members -> {
                        var counts = members.stream().collect(Collectors.groupingBy(entry -> entry.getKey().getChannelId().orElseThrow(), Collectors.counting()));
                        var eligible = new HashMap<Long, Long>();
                        for (var entry : members) {
                            var state = entry.getKey(); var member = entry.getValue(); var channel = state.getChannelId().orElseThrow();
                            if (counts.getOrDefault(channel, 0L) < 2 || !config.mutedVoice() && (state.isMuted() || state.isSelfMuted())
                                    || !config.member(member.getRoleIds().stream().map(role -> role.asString()).collect(Collectors.toSet()))) continue;
                            eligible.put(member.getId().asLong(), channel.asLong());
                        }
                        return Flux.fromIterable(tracker.update(eligible, Instant.now())).concatMap(credit ->
                                levels.award(guild, credit.userId(), LevelsRepository.Source.VOICE, config.voiceXp() * credit.minutes(),
                                                Instant.ofEpochSecond(credit.token() * 60), null, credit.token(), 0)
                                        .doOnSuccess(ignored -> tracker.acknowledge(credit)).onErrorResume(error -> Mono.empty())).then();
                    });
        });
    }
    public Mono<Void> voice(VoiceStateUpdateEvent event) {
        var state = event.getCurrent(); var tracker = trackers.get(state.getGuildId().asLong());
        if (tracker != null && (state.isDeaf() || state.isSelfDeaf() || event.getOld().isEmpty()
                || !event.getOld().orElseThrow().getChannelId().equals(state.getChannelId()))) tracker.invalidate(state.getUserId().asLong());
        return Mono.empty();
    }
    public void reset() { trackers.values().forEach(VoiceTracker::clear); }
    public void forget(long guild) { trackers.remove(guild); }
    @Override public void close() { timer.dispose(); reset(); }
}
