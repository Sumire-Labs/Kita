package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public final class MusicAccess {
    private MusicAccess() {}
    public static AudioChannel requireVoice(Member member) {
        var state = member == null ? null : member.getVoiceState();
        if (state == null || !state.inAudioChannel()) throw new IllegalArgumentException("先にボイスチャンネルに参加してください。");
        var channel = state.getChannel();
        var self = member.getGuild().getSelfMember();
        var botState = self.getVoiceState();
        if (botState != null && botState.inAudioChannel() && botState.getChannel().getIdLong() != channel.getIdLong()) {
            throw new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。");
        }
        if (!self.hasPermission(channel, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)) {
            throw new IllegalArgumentException("Kitaに接続・発言権限が必要です。");
        }
        return channel;
    }

    public static void requireVoice(CommandContext context) { requireVoice(context.member()); }
}
