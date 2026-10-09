package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class PlaybackEventOrderTest {
    private record Event(long guildId, int number) {}

    @Test void blockedFailureCannotBeOvertakenByEndButOtherGuildsCanProceed() throws Exception {
        var events = Sinks.many().unicast().<Event>onBackpressureBuffer();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var otherGuild = new CountDownLatch(1);
        var done = new CountDownLatch(2);
        var processed = Collections.synchronizedList(new ArrayList<Integer>());
        var subscription = MusicPlaybackEvents.ordered(events.asFlux(), Event::guildId, event -> {
            if (event.guildId() == 2) { otherGuild.countDown(); return; }
            if (event.number() == 1) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
            }
            processed.add(event.number());
            done.countDown();
        });
        try {
            events.tryEmitNext(new Event(1, 1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            events.tryEmitNext(new Event(1, 2));
            events.tryEmitNext(new Event(2, 1));
            assertTrue(otherGuild.await(5, TimeUnit.SECONDS));
            assertTrue(processed.isEmpty(), "End must wait for the earlier failure handler");
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(java.util.List.of(1, 2), processed);
        } finally { release.countDown(); subscription.dispose(); }
    }

    @Test void allThousandGuildsAreProcessedWithoutExhaustingGroupSubscriptions() throws Exception {
        var done = new CountDownLatch(2000);
        var last = new java.util.concurrent.ConcurrentHashMap<Long, Integer>();
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        var events = Flux.range(0, 2000).map(i -> new Event(i / 2, i % 2));
        var subscription = MusicPlaybackEvents.ordered(events, Event::guildId, event -> {
            var previous = last.put(event.guildId(), event.number());
            if (event.number() == 1 && !Integer.valueOf(0).equals(previous)) failures.incrementAndGet();
            done.countDown();
        });
        try {
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(1000, last.size());
            assertEquals(0, failures.get());
        } finally { subscription.dispose(); }
    }
}
