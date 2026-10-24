package com.sumirelabs.kita.eventlog;

import discord4j.core.event.domain.VoiceStateUpdateEvent;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

public final class EventLogVoiceListener {
    private final EventLogService service;
    public EventLogVoiceListener(EventLogService service) { this.service = service; }
    public Mono<Void> voice(VoiceStateUpdateEvent event) {
        var state = event.getCurrent();
        if (state.getUserId().asLong() == service.self() || event.getOld().isEmpty() && state.getChannelId().isEmpty()) return Mono.empty();
        long guild = state.getGuildId().asLong();
        return service.submit(guild, () -> service.config(guild).filter(EventLogConfig::enabled).flatMap(config ->
                state.getUser().flatMap(user -> {
                    if (!config.user(user.getId().asLong(), user.isBot(), false, service.self())) return Mono.empty();
                    var old = event.getOld();
                    long from = old.flatMap(value -> value.getChannelId()).map(value -> value.asLong()).orElse(0L);
                    long to = state.getChannelId().map(value -> value.asLong()).orElse(0L);
                    var details = new ArrayList<LogEntry.Field>(); LogAction action;
                    if (from != to) {
                        action = from == 0 ? LogAction.VOICE_JOIN : to == 0 ? LogAction.VOICE_LEAVE : LogAction.VOICE_MOVE;
                        details.add(new LogEntry.Field("変更前のVC", from == 0 ? "未参加" : String.valueOf(from)));
                        details.add(new LogEntry.Field("変更後のVC", to == 0 ? "未参加" : String.valueOf(to)));
                    } else {
                        if (old.isEmpty()) return Mono.empty();
                        var before = old.orElseThrow();
                        if (before.isSelfMuted() == state.isSelfMuted() && before.isSelfDeaf() == state.isSelfDeaf()
                                && before.isSelfStreaming() == state.isSelfStreaming() && before.isSelfVideoEnabled() == state.isSelfVideoEnabled()) return Mono.empty();
                        action = LogAction.VOICE_STATE;
                        details.add(new LogEntry.Field("マイクミュート", before.isSelfMuted() + " → " + state.isSelfMuted()));
                        details.add(new LogEntry.Field("スピーカーミュート", before.isSelfDeaf() + " → " + state.isSelfDeaf()));
                        details.add(new LogEntry.Field("配信・カメラ", state.isSelfStreaming() + " / " + state.isSelfVideoEnabled()));
                    }
                    var entry = new LogEntry("voice:" + user.getId().asString() + ":" + System.nanoTime(), guild, to == 0 ? from : to, 0, 0,
                            user.getId().asLong(), 0, user.isBot(), false, false, action, System.currentTimeMillis(), details, "", "", "");
                    return Mono.zip(place(event, from), place(event, to)).flatMap(locations -> {
                                var before = locations.getT1(); var after = locations.getT2();
                                if (!config.source(from, before.parent(), before.category()) || !config.source(to, after.parent(), after.category())) return Mono.empty();
                                var place = to == 0 ? before : after;
                                return service.emit(new LogEntry(entry.key(), guild, entry.channel(), place.parent(), place.category(), entry.subject(), 0, user.isBot(),
                                        false, false, action, entry.time(), details, "", "", ""));
                            });
                })));
    }
    private Mono<EventLogMessageListener.Location> place(VoiceStateUpdateEvent event, long channel) {
        return channel == 0 ? Mono.just(new EventLogMessageListener.Location(0, 0))
                : EventLogMessageListener.location(event.getClient(), channel);
    }
}
