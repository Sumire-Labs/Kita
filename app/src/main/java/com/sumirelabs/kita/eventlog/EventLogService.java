package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.rest.util.Permission;
import java.time.Duration;
import java.util.function.Supplier;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public final class EventLogService implements AutoCloseable {
    private final GatewayDiscordClient client;
    private final SettingsRepository settings;
    private final EventLogRepository repository;
    private final EventLogIngress ingress = new EventLogIngress();
    private final com.github.benmanes.caffeine.cache.Cache<Long, Mono<EventLogConfig>> configurations =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(5)).build();
    private final Disposable.Composite timers = Disposables.composite();
    public EventLogService(GatewayDiscordClient client, SettingsRepository settings, EventLogRepository repository) {
        this.client = client; this.settings = settings; this.repository = repository;
    }
    public EventLogRepository repository() { return repository; }
    public long self() { return client.getSelfId().asLong(); }
    public Mono<EventLogConfig> config(long guild) {
        return Mono.defer(() -> configurations.get(guild, id -> settings.get(id).map(EventLogConfig::new)
                .cache(value -> Duration.ofSeconds(5), error -> Duration.ZERO, () -> Duration.ZERO)));
    }
    public Mono<Void> submit(long guild, Supplier<Mono<Void>> work) { return ingress.submit(guild, work); }
    public Mono<Void> purge(long guild) { configurations.invalidate(guild); return submit(guild, () -> repository.purge(guild)); }
    public Mono<Void> emit(LogEntry entry) {
        return config(entry.guild()).flatMap(config -> {
            if (!allowed(config, entry, self()) || config.target(entry.action()) == 0) return Mono.empty();
            return repository.enqueue(config.bodies() ? entry : entry.withoutBodies(), config.target(entry.action()),
                    entry.time() + config.hours() * 3_600_000L).doOnNext(accepted -> {
                if (!accepted) org.slf4j.LoggerFactory.getLogger(getClass()).warn("EventLog outbox capacity reached in guild {}", entry.guild());
            }).then();
        });
    }
    static boolean allowed(EventLogConfig config, LogEntry entry, long self) {
        return config.action(entry.action()) && config.source(entry.channel(), entry.parent(), entry.category())
                && (entry.subject() == 0 || config.user(entry.subject(), entry.subjectBot(), entry.webhook(), self))
                && (entry.actor() == 0 || config.user(entry.actor(), entry.actorBot(), false, self));
    }
    public void start() {
        timers.add(Flux.interval(Duration.ofSeconds(1)).onBackpressureDrop().concatMap(tick -> repository.claim(System.currentTimeMillis())
                .flatMapMany(Flux::fromIterable).groupBy(pending -> pending.entry().guild())
                .flatMap(group -> group.concatMap(this::deliver), 8).then())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5)).maxBackoff(Duration.ofMinutes(1))).subscribe());
        timers.add(Flux.interval(Duration.ZERO, Duration.ofMinutes(1)).onBackpressureDrop()
                .concatMap(tick -> repository.cleanup(System.currentTimeMillis()).onErrorResume(error -> Mono.empty())).subscribe());
    }
    private Mono<Void> deliver(EventLogRepository.Pending pending) {
        var entry = pending.entry();
        return config(entry.guild()).flatMap(config -> {
            if (!allowed(config, entry, self()) || config.target(entry.action()) != pending.target()) return repository.complete(pending);
            return client.getChannelById(Access.id(pending.target())).ofType(GuildMessageChannel.class)
                    .filter(channel -> channel.getGuildId().asLong() == entry.guild())
                    .switchIfEmpty(Mono.error(new IllegalStateException("EventLog channel unavailable")))
                    .flatMap(channel -> channel.getGuild().flatMap(guild -> guild.getSelfMember())
                            .flatMap(bot -> Access.channel(bot, channel, Permission.VIEW_CHANNEL,
                                    channel instanceof ThreadChannel ? Permission.SEND_MESSAGES_IN_THREADS : Permission.SEND_MESSAGES,
                                    entry.fileText().isBlank() ? Permission.VIEW_CHANNEL : Permission.ATTACH_FILES))
                            .then(channel.createMessage(EventLogMessages.render(entry, config))))
                    .timeout(Duration.ofSeconds(40)).then(repository.complete(pending));
        }).onErrorResume(error -> {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("EventLog delivery failed in guild {}: {}", entry.guild(), error.getClass().getSimpleName());
            return repository.retry(pending, System.currentTimeMillis() + 30_000).onErrorResume(ignored -> Mono.empty());
        });
    }
    @Override public void close() { timers.dispose(); ingress.close(); configurations.invalidateAll(); }
}
