package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.tickets.TicketRecord;
import com.sumirelabs.kita.tickets.TicketRepository;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class TicketRecovery implements AutoCloseable {
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    public TicketRecovery(TicketRepository repository, LongFunction<Guild> guilds, WorkExecutor worker) {
        scheduler.scheduleWithFixedDelay(() -> {
            if (!running.compareAndSet(false, true)) return;
            if (!worker.submit(() -> {
                try {
                    for (var record : repository.pending()) {
                        var guild = guilds.apply(record.guildId());
                        if (guild == null || guild.getJDA().getStatus() != net.dv8tion.jda.api.JDA.Status.CONNECTED) continue;
                        if (record.status().equals("opening")) {
                            String topic = "kita:ticket:" + guild.getId() + ":" + record.ownerId();
                            var channel = guild.getTextChannels().stream().filter(c -> topic.equals(c.getTopic())).findFirst();
                            if (channel.isPresent()) repository.activate(record.guildId(), record.ownerId(), channel.get().getIdLong());
                            else repository.release(record.guildId(), record.ownerId());
                        } else {
                            var channel = guild.getTextChannelById(record.channelId());
                            if (channel == null || channel.getName().startsWith("closed-")) repository.close(record.guildId(), record.channelId());
                            else {
                                restorePermissions(channel, record);
                                repository.abortClose(record.guildId(), record.channelId());
                            }
                        }
                    }
                } finally { running.set(false); }
            })) running.set(false);
        }, 60, 60, TimeUnit.SECONDS);
    }

    static void restorePermissions(TextChannel channel, TicketRecord record) {
        var access = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_ATTACH_FILES);
        channel.getManager().putMemberPermissionOverride(record.ownerId(), access, java.util.List.of())
                .putRolePermissionOverride(record.supportRoleId(), access, java.util.List.of()).complete();
    }
    @Override public void close() { scheduler.shutdownNow(); }
}
