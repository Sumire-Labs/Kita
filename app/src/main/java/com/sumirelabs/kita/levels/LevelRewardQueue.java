package com.sumirelabs.kita.levels;

import discord4j.core.object.entity.Guild;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class LevelRewardQueue implements AutoCloseable {
    record Job(Guild guild, long user, long before, long after, long channel) {}
    private record Key(long guild, long user) {}
    private final Map<Key, Job> pending = new ConcurrentHashMap<>();
    private final Disposable timer;
    LevelRewardQueue(Function<Job, Mono<Void>> handler) {
        timer = Flux.interval(Duration.ofMillis(500)).onBackpressureDrop().concatMap(tick -> Flux.fromIterable(pending.entrySet())
                .take(10).flatMap(entry -> Mono.defer(() -> handler.apply(entry.getValue())).doOnSuccess(ignored -> pending.remove(entry.getKey(), entry.getValue()))
                        .onErrorResume(error -> Mono.empty()), 8)).subscribe();
    }
    void add(Job job) {
        var key = new Key(job.guild.getId().asLong(), job.user);
        pending.merge(key, job, (old, current) -> new Job(current.guild, current.user, old.before, current.after, current.channel));
    }
    @Override public void close() { timer.dispose(); }
}
