package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;

public final class PlayerRefresh implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public PlayerRefresh(MusicService music, LongFunction<Guild> guilds, WorkExecutor worker) {
        scheduler.scheduleWithFixedDelay(() -> {
            for (var entry : music.sessions().entrySet()) {
                var session = entry.getValue();
                var panel = session.beginPanelRefresh();
                if (panel == null) continue;
                if (!worker.submit(() -> {
                    try {
                        var guild = guilds.apply(entry.getKey());
                        var channel = guild == null ? null : guild.getChannelById(GuildMessageChannel.class, panel.channelId());
                        if (channel == null) { session.finishPanelRefresh(panel.messageId(), true); return; }
                        synchronized (session) {
                            if (!session.isCurrentPanel(panel.messageId())) {
                                session.finishPanelRefresh(panel.messageId(), false); return;
                            }
                            channel.editMessageById(panel.messageId(), Ui.edit(PlayerPanel.render(music, entry.getKey())))
                                    .queue(ignored -> session.finishPanelRefresh(panel.messageId(), false), error -> {
                                        boolean deleted = error instanceof ErrorResponseException response
                                                && response.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
                                        session.finishPanelRefresh(panel.messageId(), deleted);
                                        if (!deleted) LoggerFactory.getLogger(PlayerRefresh.class)
                                                .warn("Could not refresh player in guild {}", entry.getKey());
                                    });
                        }
                    } catch (RuntimeException error) {
                        session.finishPanelRefresh(panel.messageId(), false);
                        LoggerFactory.getLogger(PlayerRefresh.class).warn("Player refresh failed in guild {}", entry.getKey());
                    }
                })) session.finishPanelRefresh(panel.messageId(), false);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }
    @Override public void close() { scheduler.shutdownNow(); }
}
