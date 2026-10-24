package com.sumirelabs.kita.eventlog;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventLogRepository {
    record Pending(long id, long target, LogEntry entry, String lease) {}
    Mono<Boolean> save(MessageSnapshot snapshot, long guildLimit);
    Mono<MessageSnapshot> message(long guild, long id, long now);
    Flux<MessageSnapshot> messages(long guild, List<Long> ids, long now);
    default Mono<Boolean> enqueue(LogEntry entry, long target) { return enqueue(entry, target, entry.time() + 21_600_000L); }
    Mono<Boolean> enqueue(LogEntry entry, long target, long expires);
    Mono<List<Pending>> claim(long now);
    Mono<Void> complete(Pending pending);
    Mono<Void> retry(Pending pending, long available);
    Mono<Void> purge(long guild);
    Mono<Void> cleanup(long now);
}
