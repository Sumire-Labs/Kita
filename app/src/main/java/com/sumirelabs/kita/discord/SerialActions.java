package com.sumirelabs.kita.discord;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

/** Bounded asynchronous serialization for a guild's voice/player state. */
public final class SerialActions implements AutoCloseable {
    private record Operation<T>(Supplier<Mono<T>> call, Sinks.One<T> result) {
        Mono<Void> execute() {
            return Mono.defer(call).materialize().doOnNext(signal -> {
                if (signal.isOnError()) result.tryEmitError(java.util.Objects.requireNonNull(signal.getThrowable()));
                else if (signal.hasValue()) result.tryEmitValue(java.util.Objects.requireNonNull(signal.get()));
                else result.tryEmitEmpty();
            }).then().doFinally(type -> { if (type == SignalType.CANCEL) stop(); });
        }
        void stop() { result.tryEmitError(new IllegalStateException("処理を終了しました。")); }
    }
    private final ArrayBlockingQueue<Operation<?>> queue = new ArrayBlockingQueue<>(128);
    private final Sinks.Many<Operation<?>> input = Sinks.many().unicast().onBackpressureBuffer(queue);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Disposable subscription = input.asFlux().concatMap(Operation::execute, 1)
            .doOnDiscard(Object.class, item -> { if (item instanceof Operation<?> operation) operation.stop(); }).subscribe();
    public <T> Mono<T> submit(Supplier<Mono<T>> call) {
        return Mono.defer(() -> {
            var operation = new Operation<>(call, Sinks.<T>one());
            Sinks.EmitResult emitted;
            synchronized (input) {
                if (closed.get()) return Mono.error(new IllegalStateException("処理を終了しました。"));
                emitted = input.tryEmitNext(operation);
            }
            return emitted.isSuccess() ? operation.result.asMono() : Mono.error(new IllegalArgumentException("現在混み合っています。少し待ってください。"));
        });
    }
    @Override public void close() {
        synchronized (input) {
            if (!closed.compareAndSet(false, true)) return;
            subscription.dispose(); Operation<?> operation;
            while ((operation = queue.poll()) != null) operation.stop();
        }
    }
}
