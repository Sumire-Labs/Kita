package com.sumirelabs.kita.earthquake;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

class P2pEarthquakeProviderTest {
    @Test void historyIsSharedAndCachedAndWebsocketFiltersUnsupportedPackets() {
        var requests = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .get("/v2/history", (request, response) -> {
                    requests.incrementAndGet(); return response.sendString(Mono.just("[" + Notices.payload() + "]"));
                }).ws("/v2/ws", (in, out) -> out.sendString(Flux.just("{\"code\":555}", "invalid", Notices.payload())).then())).bindNow();
        try {
            var provider = new P2pEarthquakeProvider("http://127.0.0.1:" + server.port() + "/v2");
            var latest = Flux.range(0, 16).flatMap(index -> provider.latest(), 16).collectList().block(Duration.ofSeconds(5));
            assertEquals(16, latest.size()); assertEquals(1, requests.get());
            var notice = provider.notices().next().block(Duration.ofSeconds(5));
            assertNotNull(notice); assertEquals(EarthquakeNotice.Kind.EARTHQUAKE, notice.kind());
        } finally { server.disposeNow(); }
    }
    @Test void websocketHeartbeatKeepsQuietConnectionAliveBeyondIdleTimeout() {
        var sessions = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes.ws("/v2/ws", (in, out) -> {
            sessions.incrementAndGet();
            return out.sendString(Mono.delay(Duration.ofSeconds(3)).thenReturn(Notices.payload())).then();
        })).bindNow();
        try {
            var provider = new P2pEarthquakeProvider("http://127.0.0.1:" + server.port() + "/v2", Duration.ofMillis(200), Duration.ofSeconds(2));
            assertNotNull(provider.notices().next().block(Duration.ofSeconds(6))); assertEquals(1, sessions.get());
        } finally { server.disposeNow(); }
    }
    @Test void historyFailuresAreBrieflySharedToAvoidExceedingTheProviderRateLimit() {
        var requests = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes.get("/v2/history", (request, response) -> {
            requests.incrementAndGet(); return response.status(503).send();
        })).bindNow();
        try {
            var provider = new P2pEarthquakeProvider("http://127.0.0.1:" + server.port() + "/v2");
            Flux.range(0, 16).flatMap(index -> provider.latest().onErrorResume(error -> Mono.empty()), 16).then().block(Duration.ofSeconds(5));
            assertEquals(1, requests.get());
        } finally { server.disposeNow(); }
    }
    @Test void reconnectBackfillsMissedBulletinsWithoutFetchingHistoryOnInitialConnection() {
        var sessions = new AtomicInteger(); var history = new AtomicInteger();
        var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .get("/v2/history", (request, response) -> {
                    history.incrementAndGet();
                    var payload = Notices.payload().replace("2026/10/12 10:00:00",
                            java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo")).format(formatter)).replace("test-id", "missed");
                    return response.sendString(Mono.just("[" + payload + "]"));
                }).ws("/v2/ws", (in, out) -> {
                    int session = sessions.incrementAndGet();
                    return out.sendString(Mono.just(Notices.payload().replace("test-id", "live" + session))).then();
                })).bindNow();
        try {
            var provider = new P2pEarthquakeProvider("http://127.0.0.1:" + server.port() + "/v2");
            var received = provider.notices().take(3).collectList().block(Duration.ofSeconds(10));
            assertEquals(2, sessions.get()); assertEquals(1, history.get());
            assertTrue(received.stream().anyMatch(notice -> notice.key().equals("p2pquake:quake:missed")));
        } finally { server.disposeNow(); }
    }
}
