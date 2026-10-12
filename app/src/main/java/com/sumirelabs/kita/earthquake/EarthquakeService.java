package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.GatewayDiscordClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public final class EarthquakeService implements AutoCloseable {
    private record Pending(EarthquakeNotice notice, long retryAt) {}
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final EarthquakeProvider provider;
    private final EarthquakeRepository repository;
    private final EarthquakeDelivery delivery;
    private final Disposable.Composite subscriptions = Disposables.composite();
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
    public EarthquakeService(GatewayDiscordClient client, SettingsRepository settings, EarthquakeRepository repository, EarthquakeProvider provider) {
        this.provider = provider; this.repository = repository; delivery = new EarthquakeDelivery(client, settings, repository);
    }
    public EarthquakeProvider provider() { return provider; }
    public void start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Earthquake receiver already started");
        subscriptions.add(repository.recent(Instant.now().minus(Duration.ofMinutes(30))).filter(notice -> notice.fresh(Instant.now()))
                .doOnNext(this::offer).thenMany(provider.notices()).concatMap(notice -> {
                    if (!notice.fresh(Instant.now())) return Mono.empty();
                    return repository.accept(notice).filter(Boolean::booleanValue).doOnNext(accepted -> offer(notice)).then();
                }).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5)).maxBackoff(Duration.ofMinutes(1)))
                .subscribe(ignored -> {}, error -> org.slf4j.LoggerFactory.getLogger(getClass()).error("Earthquake receiver stopped")));
        subscriptions.add(Flux.interval(Duration.ofMillis(250)).onBackpressureDrop().concatMap(tick ->
                Flux.fromIterable(pending.values()).filter(slot -> slot.retryAt <= System.currentTimeMillis()).take(8).flatMap(slot -> {
                    var notice = slot.notice;
                    if (!notice.fresh(Instant.now())) { pending.remove(notice.key(), slot); return Mono.empty(); }
                    return delivery.send(notice, () -> pending.get(notice.key()) == slot).doOnNext(sent -> {
                        if (sent) pending.remove(notice.key(), slot);
                        else pending.computeIfPresent(notice.key(), (key, current) ->
                                current == slot ? new Pending(notice, System.currentTimeMillis() + 10_000) : current);
                    });
                }, 4)).subscribe());
        subscriptions.add(Flux.interval(Duration.ofHours(6)).concatMap(tick -> repository.prune(Instant.now().minus(Duration.ofDays(7)))
                .onErrorResume(error -> Mono.empty())).subscribe());
    }
    private void offer(EarthquakeNotice notice) {
        if (pending.size() >= 2048 && !pending.containsKey(notice.key())) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Earthquake delivery queue full; bulletin remains persisted");
            return;
        }
        pending.compute(notice.key(), (key, old) -> old == null || notice.supersedes(old.notice) ? new Pending(notice, 0) : old);
    }
    @Override public void close() { subscriptions.dispose(); pending.clear(); }
}