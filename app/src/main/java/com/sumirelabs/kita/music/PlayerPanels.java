package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.LoggerFactory;

final class PlayerPanels {
    private PlayerPanels() {}

    static void publish(CommandContext context, MusicService music) {
        long guildId = context.guild().getIdLong();
        var panel = PlayerPanel.render(music, guildId);
        var message = context.slash() == null ? context.channel().sendMessage(panel).complete()
                : context.slash().getHook().sendMessage(panel).complete();
        var previous = music.session(guildId).replacePanel(message.getChannelIdLong(), message.getIdLong());
        if (previous.messageId() == 0) return;
        var channel = context.guild().getChannelById(GuildMessageChannel.class, previous.channelId());
        if (channel != null) channel.deleteMessageById(previous.messageId()).queue(ignored -> {}, error ->
                LoggerFactory.getLogger(PlayerPanels.class).warn("Could not delete previous player in guild {}", guildId));
    }
}
