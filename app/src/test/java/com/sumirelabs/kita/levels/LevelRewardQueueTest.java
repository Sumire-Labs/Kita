package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class LevelRewardQueueTest {
    private Guild guild() { var guild = mock(Guild.class); when(guild.getId()).thenReturn(Snowflake.of(1)); return guild; }
    @Test void combinesWaitingUpdatesForTheSameMember() throws Exception {
        var result = new CompletableFuture<LevelRewardQueue.Job>();
        try (var queue = new LevelRewardQueue(job -> Mono.fromRunnable(() -> result.complete(job)))) {
            var guild = guild(); queue.add(new LevelRewardQueue.Job(guild, 1, 100, 200, 10)); queue.add(new LevelRewardQueue.Job(guild, 1, 200, 220, 20));
            var job = result.get(5, TimeUnit.SECONDS);
            assertEquals(100, job.before()); assertEquals(220, job.after()); assertEquals(20, job.channel());
        }
    }
    @Test void pendingDiscordWorkHasBoundedConcurrencyAndDoesNotBlockTheCaller() throws Exception {
        var entered = new CountDownLatch(8); var release = Sinks.<Void>one(); var done = new CountDownLatch(24);
        var active = new AtomicInteger(); var max = new AtomicInteger();
        try (var queue = new LevelRewardQueue(job -> Mono.defer(() -> {
            max.accumulateAndGet(active.incrementAndGet(), Math::max); entered.countDown();
            return release.asMono().doOnTerminate(() -> { active.decrementAndGet(); done.countDown(); });
        }))) {
            var guild = guild();
            for (int user = 0; user < 24; user++) queue.add(new LevelRewardQueue.Job(guild, user, 100, 200, 10));
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(8, active.get());
            release.tryEmitEmpty(); assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(max.get() <= 8);
        } finally { release.tryEmitEmpty(); }
    }
}
