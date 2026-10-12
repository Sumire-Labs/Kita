package com.sumirelabs.kita.earthquake;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.util.retry.Retry;
import reactor.core.scheduler.Schedulers;

public final class P2pEarthquakeProvider implements EarthquakeProvider {
    private final HttpClient http = HttpClient.create().responseTimeout(Duration.ofSeconds(20));
    private final P2pNoticeParser parser = new P2pNoticeParser();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicLong lastAlive = new AtomicLong(), resumeAfter = new AtomicLong();
    private final String base;
    private final Duration pingEvery, idleTimeout;
    private final Mono<EarthquakeNotice> latest;
    public P2pEarthquakeProvider() { this("https://api.p2pquake.net/v2"); }
    P2pEarthquakeProvider(String base) { this(base, Duration.ofSeconds(30), Duration.ofSeconds(75)); }
    P2pEarthquakeProvider(String base, Duration pingEvery, Duration idleTimeout) {
        this.base = base; this.pingEvery = pingEvery; this.idleTimeout = idleTimeout;
        latest = Mono.defer(() -> http.get().uri(base + "/history?codes=551&limit=1").responseSingle((response, content) -> {
            if (response.status().code() != 200) return Mono.error(new IllegalStateException("Earthquake history unavailable"));
            return content.asString(java.nio.charset.StandardCharsets.UTF_8).publishOn(Schedulers.parallel()).flatMap(body -> Mono.fromCallable(() -> {
                var rows = new ObjectMapper().readTree(body);
                if (!rows.isArray() || rows.isEmpty()) throw new IllegalArgumentException("地震情報が見つかりません。");
                return parser.parse(rows.get(0)).orElseThrow(() -> new IllegalArgumentException("地震情報を読み取れませんでした。"));
            }));
        })).timeout(Duration.ofSeconds(25)).cache(value -> Duration.ofSeconds(30), error -> Duration.ZERO, () -> Duration.ZERO);
    }
    @Override public String name() { return "P2P地震情報（無料・EEW警報／地震／津波）"; }
    @Override public String status() { return connected.get() ? "受信接続中" : "接続準備／再接続中"; }
    @Override public Mono<EarthquakeNotice> latest() { return latest; }
    @Override public Flux<EarthquakeNotice> notices() {
        return Flux.defer(() -> HttpClient.create().websocket(WebsocketClientSpec.builder().maxFramePayloadLength(1024 * 1024).build())
                .uri(base.replaceFirst("^http", "ws") + "/ws").handle((in, out) -> {
                    connected.set(true); lastAlive.set(System.currentTimeMillis());
                    org.slf4j.LoggerFactory.getLogger(getClass()).info("Earthquake feed connected to P2Pquake");
                    long since = resumeAfter.getAndSet(0);
                    var ping = out.sendObject(Flux.interval(pingEvery).map(tick -> new PingWebSocketFrame())).then();
                    return in.aggregateFrames(1024 * 1024).receiveFrames().doOnNext(frame -> lastAlive.set(System.currentTimeMillis()))
                            .timeout(idleTimeout).takeUntilOther(ping)
                            .filter(frame -> frame instanceof TextWebSocketFrame).map(frame -> ((TextWebSocketFrame) frame).text())
                            .publishOn(Schedulers.parallel(), 16)
                            .concatMap(body -> Mono.fromCallable(() -> parser.parse(body)).flatMap(Mono::justOrEmpty)
                                    .onErrorResume(error -> { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Ignored malformed earthquake bulletin"); return Mono.empty(); }))
                            .mergeWith(recover(since));
                }).doFinally(signal -> {
                    connected.set(false);
                    if (lastAlive.get() != 0) resumeAfter.compareAndSet(0, lastAlive.get());
                })).repeatWhen(completed -> completed.delayElements(Duration.ofSeconds(5)))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5)).maxBackoff(Duration.ofMinutes(1))
                        .doBeforeRetry(signal -> org.slf4j.LoggerFactory.getLogger(getClass()).warn("Earthquake feed reconnecting: {}",
                                signal.failure().getClass().getSimpleName())));
    }
    private Flux<EarthquakeNotice> recover(long since) {
        if (since == 0) return Flux.empty();
        return http.get().uri(base + "/history?codes=551&codes=552&codes=556&limit=50")
                .responseSingle((response, content) -> response.status().code() == 200 ? content.asString()
                        : Mono.error(new IllegalStateException("Earthquake recovery unavailable")))
                .timeout(Duration.ofSeconds(25)).publishOn(Schedulers.parallel()).flatMap(body -> Mono.fromCallable(() -> new ObjectMapper().readTree(body)))
                .flatMapMany(Flux::fromIterable).concatMap(row -> Mono.fromCallable(() -> parser.parse(row)).flatMap(Mono::justOrEmpty))
                .filter(notice -> notice.issuedMs() >= since - 2000)
                .onErrorResume(error -> {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Earthquake recovery failed: {}", error.getClass().getSimpleName());
                    return Flux.empty();
                });
    }
}
