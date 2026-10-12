package com.sumirelabs.kita.earthquake;

import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EarthquakeRepository {
    record Delivery(long messageId, String token, String contentToken) {}
    record Lease(String event, long guild, long channel, String owner) {}
    Mono<Boolean> accept(EarthquakeNotice notice);
    Flux<EarthquakeNotice> recent(Instant since);
    Mono<Delivery> delivery(String key, long guild, long channel);
    Mono<Lease> claim(EarthquakeNotice notice, long guild, long channel);
    Mono<Void> sent(Lease lease, EarthquakeNotice notice, long messageId);
    Mono<Void> release(Lease lease);
    Mono<Void> prune(Instant before);
}