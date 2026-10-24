package com.sumirelabs.kita.earthquake;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Providers normalize external bulletins; routing, persistence and Discord UI remain provider independent. */
public interface EarthquakeProvider {
    String name();
    String status();
    Flux<EarthquakeNotice> notices();
    Mono<EarthquakeNotice> latest();
}