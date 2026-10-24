package com.sumirelabs.kita.discord;

import java.util.concurrent.Callable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Isolation for blocking third-party SDKs, subprocesses, files, and native image rendering. */
public final class ReactiveWork {
    private static final Scheduler IO = Schedulers.newBoundedElastic(16, 256, "kita-io");
    private static final Scheduler CPU = Schedulers.newBoundedElastic(2, 64, "kita-render");
    private ReactiveWork() {}
    public static <T> Mono<T> io(Callable<T> work) { return Mono.fromCallable(work).subscribeOn(IO); }
    public static <T> Mono<T> cpu(Callable<T> work) { return Mono.fromCallable(work).subscribeOn(CPU); }
    public static void close() { IO.dispose(); CPU.dispose(); }
}
