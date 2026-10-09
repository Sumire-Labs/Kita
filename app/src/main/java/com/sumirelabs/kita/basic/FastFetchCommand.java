package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Ui;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import oshi.SystemInfo;

public final class FastFetchCommand implements Command {
    private final String version;
    private final SystemInfo system = new SystemInfo();

    public FastFetchCommand(String version) { this.version = version; }
    @Override public CommandData definition() { return Commands.slash("fastfetch", "Kitaの動作環境を表示"); }

    @Override public void execute(CommandContext context) {
        var runtime = Runtime.getRuntime();
        var uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        var cpu = system.getHardware().getProcessor().getProcessorIdentifier().getName().strip();
        var body = "```text\n"
                + "    K  K   Kita " + version + "\n"
                + "    K K    OS     " + system.getOperatingSystem().getFamily() + "\n"
                + "    KK     CPU    " + cpu + "\n"
                + "    K K    Java   " + Runtime.version() + "\n"
                + "    K  K   Heap   " + mib(runtime.totalMemory() - runtime.freeMemory()) + "/"
                + mib(runtime.maxMemory()) + " MiB\n"
                + "           Uptime " + uptime.toDays() + "d " + uptime.toHoursPart() + "h "
                + uptime.toMinutesPart() + "m\n"
                + "           Guilds " + context.jda().getShardManager().getGuildCache().size() + "\n"
                + "           Shard  " + context.jda().getShardInfo().getShardId() + "\n```";
        context.reply("FastFetch", body);
    }

    private static long mib(long bytes) { return bytes / (1024 * 1024); }
}
