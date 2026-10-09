package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Access;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.AudioChannel;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

public final class MusicAccess {
    private MusicAccess() {}
    public static Mono<AudioChannel> requireVoice(Member member) {
        return member.getVoiceState().flatMap(state -> state.getChannel())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("先にボイスチャンネルに参加してください。")))
                .flatMap(channel -> member.getGuild().flatMap(guild -> Access.member(member.getClient(), guild.getId().asLong(),
                        member.getClient().getSelfId().asLong())).flatMap(self -> self.getVoiceState()
                        .flatMap(state -> Mono.justOrEmpty(state.getChannelId()))
                        .filter(id -> !id.equals(channel.getId()))
                        .flatMap(id -> Mono.<Void>error(new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。")))
                        .then(Access.channel(self, channel, Permission.CONNECT, Permission.SPEAK)))
                        .then(Access.channel(member, channel, Permission.VIEW_CHANNEL, Permission.CONNECT))
                        .thenReturn(channel));
    }
}
