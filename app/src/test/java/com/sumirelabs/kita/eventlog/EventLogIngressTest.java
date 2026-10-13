package com.sumirelabs.kita.eventlog;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class EventLogIngressTest {
    @Test void guildOrderingDoesNotBlockOtherStripes() {
        var release = Sinks.<Void>one(); var order = new ArrayList<Integer>();
        try (var ingress = new EventLogIngress()) {
            var first = ingress.submit(1, () -> release.asMono().doOnSuccess(ignored -> order.add(1))).toFuture();
            var second = ingress.submit(1, () -> Mono.fromRunnable(() -> order.add(2))).toFuture();
            ingress.submit(2, () -> Mono.empty()).block(Duration.ofSeconds(2));
            assertTrue(order.isEmpty()); release.tryEmitEmpty(); first.join(); second.join();
            assertEquals(java.util.List.of(1, 2), order);
        }
    }
    @Test void globalCapacityAndShutdownAreBounded() {
        var ingress = new EventLogIngress(); var tasks = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        for (int index = 0; index < 512; index++) tasks.add(ingress.submit(1, Mono::never).toFuture());
        assertThrows(IllegalStateException.class, () -> ingress.submit(2, Mono::empty).block(Duration.ofSeconds(1)));
        ingress.close(); assertTrue(tasks.stream().allMatch(java.util.concurrent.CompletableFuture::isCompletedExceptionally));
    }
}