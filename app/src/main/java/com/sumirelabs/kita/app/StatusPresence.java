package com.sumirelabs.kita.app;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

public final class StatusPresence implements AutoCloseable {
    private final String version;
    private final OperatingSystem operatingSystem = new SystemInfo().getOperatingSystem();
    private final int processId = Math.toIntExact(ProcessHandle.current().pid());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            work -> Thread.ofPlatform().daemon(true).name("kita-presence").unstarted(work));

    public StatusPresence(String version) { this.version = version; }

    public Activity current() {
        var process = operatingSystem.getProcess(processId);
        if (process == null) throw new IllegalStateException("Bot process memory is unavailable");
        long megabytes = process.getResidentMemory() / 1_000_000;
        return Activity.playing("v" + version + " | " + megabytes + "MB");
    }

    public void start(ShardManager shards) {
        scheduler.scheduleWithFixedDelay(() -> {
            try { shards.setActivity(current()); }
            catch (Exception error) { LoggerFactory.getLogger(StatusPresence.class).warn("Presence update failed", error); }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override public void close() { scheduler.shutdownNow(); }
}
