package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Guild;

final class LevelRewardQueue implements AutoCloseable {
    record Job(Guild guild, long user, long before, long after, long channel) {}
    private record Key(long guild, long user) {}
    private final Map<Key, Job> pending = new ConcurrentHashMap<>();
    private final java.util.Set<Key> active = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(8);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    LevelRewardQueue(WorkExecutor worker, Consumer<Job> handler) {
        timer.scheduleWithFixedDelay(() -> {
            int submitted = 0;
            for (var entry : pending.entrySet()) {
                if (submitted >= 10) break;
                var key = entry.getKey(); var job = entry.getValue();
                if (!capacity.tryAcquire()) break;
                if (!active.add(key)) { capacity.release(); continue; }
                if (worker.submit(() -> {
                    try { handler.accept(job); pending.remove(key, job); }
                    finally { active.remove(key); capacity.release(); }
                })) submitted++;
                else { active.remove(key); capacity.release(); break; }
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }
    void add(Job job) {
        var key = new Key(job.guild.getIdLong(), job.user);
        pending.merge(key, job, (old, current) -> new Job(current.guild, current.user, old.before, current.after, current.channel));
    }
    @Override public void close() { timer.shutdownNow(); }
}
