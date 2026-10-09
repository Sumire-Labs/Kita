package com.sumirelabs.kita.app;

import com.sumirelabs.kita.discord.ReactiveWork;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import java.time.Duration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

public final class StatusPresence implements AutoCloseable {
    private final String version;
    private final OperatingSystem operatingSystem = new SystemInfo().getOperatingSystem();
    private final int processId = Math.toIntExact(ProcessHandle.current().pid());
    private Disposable timer;
    public StatusPresence(String version) { this.version = version; }
    public ClientActivity current() {
        var process = operatingSystem.getProcess(processId);
        if (process == null) throw new IllegalStateException("Bot process memory is unavailable");
        return ClientActivity.playing("v" + version + " | " + process.getResidentMemory() / 1_000_000 + "MB");
    }
    public void start(GatewayDiscordClient client) {
        timer = Flux.interval(Duration.ofSeconds(30)).onBackpressureDrop()
                .concatMap(tick -> ReactiveWork.io(this::current).flatMap(activity -> client.updatePresence(ClientPresence.online(activity)))
                        .onErrorResume(error -> Mono.empty())).subscribe();
    }
    @Override public void close() { if (timer != null) timer.dispose(); }
}
