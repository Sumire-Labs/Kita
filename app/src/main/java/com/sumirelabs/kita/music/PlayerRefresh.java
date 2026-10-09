package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.entities.Guild;

public final class PlayerRefresh implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public PlayerRefresh(MusicService music, LongFunction<Guild> guilds, WorkExecutor worker) {
        scheduler.scheduleWithFixedDelay(() -> {
            for (var entry : music.sessions().entrySet()) {
                var session = entry.getValue();
                long channelId;
                long messageId;
                synchronized (session) {
                    if (session.panelMessage == 0 || session.queue.current() == null) continue;
                    channelId = session.panelChannel; messageId = session.panelMessage;
                }
                worker.submit(() -> {
                    var guild = guilds.apply(entry.getKey());
                    if (guild == null) return;
                    var channel = guild.getTextChannelById(channelId);
                    if (channel == null) return;
                    channel.editMessageById(messageId, Ui.edit(PlayerPanel.render(music, entry.getKey())))
                            .queue(ignored -> {}, error -> {
                                synchronized (session) { if (session.panelMessage == messageId) session.panelMessage = 0; }
                            });
                });
            }
        }, 15, 15, TimeUnit.SECONDS);
    }
    @Override public void close() { scheduler.shutdownNow(); }
}
