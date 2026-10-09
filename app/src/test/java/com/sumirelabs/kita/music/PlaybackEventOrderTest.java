package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.discord.SerialActions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class PlaybackEventOrderTest {
    @Test void pendingFailureCannotBeOvertakenByEndButOtherGuildsCanProceed() {
        var release = Sinks.<Void>one(); var processed = new ArrayList<Integer>();
        try (var first = new SerialActions(); var other = new SerialActions()) {
            var failure = first.submit(() -> release.asMono().doOnSuccess(ignored -> processed.add(1))).toFuture();
            var end = first.submit(() -> Mono.fromRunnable(() -> processed.add(2))).toFuture();
            other.submit(() -> Mono.just(10)).block(Duration.ofSeconds(2));
            assertTrue(processed.isEmpty()); assertFalse(end.isDone());
            release.tryEmitEmpty(); failure.join(); end.join(); assertEquals(List.of(1, 2), processed);
        }
    }
    @Test void allThousandGuildsAreProcessedInOrderWithoutAThreadPerGuild() {
        var actors = new ConcurrentHashMap<Integer, SerialActions>(); var last = new ConcurrentHashMap<Integer, Integer>();
        try {
            Flux.range(0, 2000).flatMap(index -> actors.computeIfAbsent(index / 2, ignored -> new SerialActions())
                    .submit(() -> Mono.fromRunnable(() -> {
                        var previous = last.put(index / 2, index % 2);
                        if (index % 2 == 1) assertEquals(0, previous);
                    })), 128).then().block(Duration.ofSeconds(10));
            assertEquals(1000, last.size());
        } finally { actors.values().forEach(SerialActions::close); }
    }
}
