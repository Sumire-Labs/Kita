package com.sumirelabs.kita.earthquake;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class MemoryEarthquakes implements EarthquakeRepository {
    private record Key(String event, long guild, long channel) {}
    final Map<String, EarthquakeNotice> events = new ConcurrentHashMap<>();
    private final Map<Key, Delivery> deliveries = new ConcurrentHashMap<>();
    final Map<String, Lease> leases = new ConcurrentHashMap<>();
    public Mono<Boolean> accept(EarthquakeNotice notice) { return Mono.fromSupplier(() -> {
        var previous = events.putIfAbsent(notice.key(), notice);
        if (previous == null) return true;
        if (!notice.supersedes(previous)) return false;
        events.put(notice.key(), notice); return true;
    }); }
    public Flux<EarthquakeNotice> recent(Instant since) { return Flux.fromIterable(events.values()); }
    public Mono<Delivery> delivery(String key, long guild, long channel) { return Mono.defer(() -> Mono.justOrEmpty(deliveries.get(new Key(key, guild, channel)))); }
    public Mono<Lease> claim(EarthquakeNotice notice, long guild, long channel) {
        return delivery(notice.key(), guild, channel).defaultIfEmpty(new Delivery(0, "", "")).flatMap(previous -> {
            if (previous.token().equals(notice.token())) return Mono.empty();
            var lease = new Lease(notice.key(), guild, channel, UUID.randomUUID().toString()); leases.put(lease.owner(), lease); return Mono.just(lease);
        });
    }
    public Mono<Void> sent(Lease lease, EarthquakeNotice notice, long message) { return Mono.fromRunnable(() ->
            deliveries.put(new Key(lease.event(), lease.guild(), lease.channel()), new Delivery(message, notice.token(), notice.contentToken()))); }
    public Mono<Void> release(Lease lease) { return Mono.fromRunnable(() -> leases.remove(lease.owner())); }
    public Mono<Void> prune(Instant before) { return Mono.empty(); }
}