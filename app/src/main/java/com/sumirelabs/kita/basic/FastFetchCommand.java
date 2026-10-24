package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.*;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import reactor.core.publisher.Mono;
import oshi.SystemInfo;

public final class FastFetchCommand implements Command {
    private final String version;
    private final SystemInfo system = new SystemInfo();
    public FastFetchCommand(String version) { this.version = version; }
    @Override public ApplicationCommandRequest definition() { return Commands.slash("fastfetch", "Kitaの動作環境を表示"); }
    @Override public Mono<Void> execute(CommandContext context) {
        return context.client().getGuilds().count().flatMap(guilds -> ReactiveWork.io(() -> {
            var runtime = Runtime.getRuntime(); var uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
            String cpu = system.getHardware().getProcessor().getProcessorIdentifier().getName().strip();
            return "```text\n    K  K   Kita " + version + "\n    K K    OS     " + system.getOperatingSystem().getFamily()
                    + "\n    KK     CPU    " + cpu + "\n    K K    Java   " + Runtime.version()
                    + "\n    K  K   Heap   " + mib(runtime.totalMemory() - runtime.freeMemory()) + "/" + mib(runtime.maxMemory()) + " MiB"
                    + "\n           Uptime " + uptime.toDays() + "d " + uptime.toHoursPart() + "h " + uptime.toMinutesPart()
                    + "m\n           Guilds " + guilds + "\n           Shard  " + context.shardId() + "\n```";
        })).flatMap(body -> context.reply("FastFetch", body));
    }
    private static long mib(long bytes) { return bytes / (1024 * 1024); }
}
