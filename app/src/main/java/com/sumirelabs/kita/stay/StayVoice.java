package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.SerialActions;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.music.MusicService;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.rest.util.Permission;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

final class StayVoice implements StayService.Voice, AutoCloseable {
    private final GatewayDiscordClient client;
    private final MusicService music;
    private final Map<Long, SerialActions> operations = new ConcurrentHashMap<>();
    StayVoice(GatewayDiscordClient client, MusicService music) { this.client = client; this.music = music; }
    static Mono<Long> require(Member member) {
        return member.getVoiceState().flatMap(state -> state.getChannel())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("先にボイスチャンネルに参加してください。")))
                .flatMap(channel -> Access.channel(member, channel, Permission.VIEW_CHANNEL, Permission.CONNECT)
                        .then(member.getGuild().flatMap(guild -> guild.getSelfMember())).flatMap(self -> self.getVoiceState()
                                .flatMap(state -> Mono.justOrEmpty(state.getChannelId())).filter(id -> !id.equals(channel.getId()))
                                .flatMap(id -> Mono.error(new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。")))
                                .thenReturn(channel.getId().asLong())));
    }
    @Override public <T> Mono<T> operate(long guild, Supplier<Mono<T>> action) {
        return music == null ? operations.computeIfAbsent(guild, ignored -> new SerialActions()).submit(action) : music.voiceOperation(guild, action);
    }
    @Override public Mono<Void> connect(long guild, long channel) {
        return client.getChannelById(Access.id(channel)).ofType(VoiceChannel.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("ボイスチャンネルが見つかりません。")))
                .flatMap(voice -> Access.member(client, guild, client.getSelfId().asLong())
                        .flatMap(self -> Access.channel(self, voice, Permission.VIEW_CHANNEL, Permission.CONNECT))
                        .then(music == null ? voice.sendConnectVoiceState(false, false) : music.connect(voice)))
                .doOnSuccess(ignored -> { if (music != null) music.staying(guild, channel); });
    }
    @Override public Mono<Void> disconnect(long guild) {
        return client.getGuildById(Access.id(guild)).flatMap(server -> server.getSelfMember())
                .flatMap(self -> self.getVoiceState()).flatMap(state -> state.getChannel())
                .flatMap(voice -> voice.sendDisconnectVoiceState()).then(music == null ? Mono.empty() : music.endStay(guild));
    }
    @Override public Mono<Long> maintain(long guild, long channel) {
        return client.getGuildById(Access.id(guild)).flatMap(server -> server.getSelfMember())
                .flatMap(self -> self.getVoiceState()).flatMap(state -> Mono.justOrEmpty(state.getChannelId()))
                .map(id -> id.asLong()).doOnNext(actual -> { if (music != null) music.staying(guild, actual); })
                .switchIfEmpty(connect(guild, channel).thenReturn(channel));
    }
    @Override public Mono<Void> refresh(long guild, StaySession.View view) {
        if (view.panelMessage() == 0) return Mono.empty();
        return client.getMessageById(Access.id(view.panelChannel()), Access.id(view.panelMessage()))
                .flatMap(message -> message.edit(Ui.edit(StayPanel.render(view)))).then().onErrorResume(error -> Mono.empty());
    }
    @Override public void close() { operations.values().forEach(SerialActions::close); }
}
