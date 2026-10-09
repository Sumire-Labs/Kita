package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;

class LevelRewardQueueTest {
    private Guild guild() { return (Guild) Proxy.newProxyInstance(Guild.class.getClassLoader(), new Class<?>[]{Guild.class},
            (proxy, method, arguments) -> { if (method.getName().equals("getIdLong")) return 1L; throw new AssertionError(method.getName()); }); }
    @Test void combinesWaitingUpdatesForTheSameMember() throws Exception {
        var result = new CompletableFuture<LevelRewardQueue.Job>();
        try (var worker = new WorkExecutor(); var queue = new LevelRewardQueue(worker, result::complete)) {
            var guild = guild();
            queue.add(new LevelRewardQueue.Job(guild, 1, 100, 200, 10));
            queue.add(new LevelRewardQueue.Job(guild, 1, 200, 220, 20));
            var job = result.get(5, TimeUnit.SECONDS);
            assertEquals(100, job.before()); assertEquals(220, job.after()); assertEquals(20, job.channel());
        }
    }
    @Test void slowDiscordWorkCannotOccupyEveryBackgroundWorker() throws Exception {
        var entered = new CountDownLatch(8); var release = new CountDownLatch(1); var done = new CountDownLatch(24);
        var active = new AtomicInteger(); var max = new AtomicInteger();
        try (var worker = new WorkExecutor()) {
            try (var queue = new LevelRewardQueue(worker, job -> {
                int current = active.incrementAndGet(); max.accumulateAndGet(current, Math::max); entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                finally { active.decrementAndGet(); done.countDown(); }
            })) {
                var guild = guild();
                for (int user = 0; user < 24; user++) queue.add(new LevelRewardQueue.Job(guild, user, 100, 200, 10));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(8, active.get());
                var unrelated = new CountDownLatch(1); assertTrue(worker.submit(unrelated::countDown));
                assertTrue(unrelated.await(1, TimeUnit.SECONDS));
                release.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(max.get() <= 8);
            } finally { release.countDown(); }
        }
    }
}
