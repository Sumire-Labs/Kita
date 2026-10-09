package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.music.MusicService;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.LoggerFactory;

final class StayVoice implements StayService.Voice {
    private final LongFunction<Guild> guilds;
    private final MusicService music;
    StayVoice(LongFunction<Guild> guilds, MusicService music) { this.guilds = guilds; this.music = music; }
    static long require(Member member) {
        var state = member == null ? null : member.getVoiceState();
        if (state == null || !state.inAudioChannel()) throw new IllegalArgumentException("先にボイスチャンネルに参加してください。");
        var channel = state.getChannel();
        if (!member.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) {
            throw new IllegalArgumentException("ボイスチャンネルの閲覧・接続権限が必要です。");
        }
        var self = member.getGuild().getSelfMember();
        var botState = self.getVoiceState();
        if (botState != null && botState.inAudioChannel() && botState.getChannel().getIdLong() != channel.getIdLong()) {
            throw new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。");
        }
        return channel.getIdLong();
    }
    @Override public void operate(long guild, Runnable action) {
        if (music == null) action.run();
        else music.voiceOperation(guild, action);
    }
    @Override public void connect(long guildId, long channelId) {
        var guild = guilds.apply(guildId);
        var channel = guild == null ? null : guild.getChannelById(AudioChannel.class, channelId);
        if (channel == null) throw new IllegalArgumentException("ボイスチャンネルが見つかりません。");
        if (!guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) {
            throw new IllegalArgumentException("Kitaにボイスチャンネルの閲覧・接続権限が必要です。");
        }
        guild.getJDA().getDirectAudioController().connect(channel);
        if (music != null) music.staying(guildId, channelId);
    }
    @Override public void disconnect(long guildId) {
        var guild = guilds.apply(guildId);
        if (guild != null) guild.getJDA().getDirectAudioController().disconnect(guild);
        if (music != null) music.endStay(guildId);
    }
    @Override public long maintain(long guildId, long channelId) {
        var guild = guilds.apply(guildId);
        if (guild == null) return channelId;
        var self = guild.getSelfMember();
        var state = self.getVoiceState();
        if (state != null && state.inAudioChannel()) {
            long actual = state.getChannel().getIdLong();
            if (music != null) music.staying(guildId, actual);
            return actual;
        }
        var channel = guild.getChannelById(AudioChannel.class, channelId);
        if (channel != null && self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) connect(guildId, channelId);
        return channelId;
    }
    @Override public void refresh(long guildId, StaySession.View view) {
        var guild = guilds.apply(guildId);
        var channel = guild == null ? null : guild.getChannelById(GuildMessageChannel.class, view.panelChannel());
        if (channel == null || view.panelMessage() == 0) return;
        channel.editMessageById(view.panelMessage(), Ui.edit(StayPanel.render(view))).queue(ignored -> {}, error ->
                LoggerFactory.getLogger(StayVoice.class).debug("Could not refresh stay panel in guild {}", guildId));
    }
}
