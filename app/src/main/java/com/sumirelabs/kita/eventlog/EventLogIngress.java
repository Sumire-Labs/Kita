package com.sumirelabs.kita.eventlog;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class EventLogIngress implements AutoCloseable {
    private record Job(long guild, Supplier<Mono<Void>> work, Sinks.One<Void> done) {}
    private final Semaphore capacity = new Semaphore(512);
    private final Sinks.Many<Job> input = Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(512));
    private final Disposable subscription;
    private boolean closed;
    public EventLogIngress() {
        subscription = input.asFlux().groupBy(job -> Math.floorMod(Long.hashCode(job.guild), 16), 16)
                .flatMap(group -> group.concatMap(job -> Mono.defer(job.work).materialize().doOnNext(signal -> {
                    if (signal.isOnError()) job.done.tryEmitError(java.util.Objects.requireNonNull(signal.getThrowable()));
                    else job.done.tryEmitEmpty();
                }).then().doFinally(signal -> {
                    capacity.release();
                    if (signal == reactor.core.publisher.SignalType.CANCEL) job.done.tryEmitError(new IllegalStateException("EventLog stopped"));
                }), 1), 16)
                .doOnDiscard(Job.class, job -> { capacity.release(); job.done.tryEmitError(new IllegalStateException("EventLog stopped")); }).subscribe();
    }
    public Mono<Void> submit(long guild, Supplier<Mono<Void>> work) {
        return Mono.defer(() -> {
            var job = new Job(guild, work, Sinks.one());
            synchronized (input) {
                if (closed) return Mono.empty();
                if (!capacity.tryAcquire()) return Mono.error(new IllegalStateException("EventLog queue full"));
                if (input.tryEmitNext(job).isFailure()) { capacity.release(); return Mono.error(new IllegalStateException("EventLog queue full")); }
            }
            return job.done.asMono();
        });
    }
    @Override public void close() { synchronized (input) { closed = true; subscription.dispose(); } }
}
