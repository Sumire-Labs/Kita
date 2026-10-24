package com.sumirelabs.kita.discord;

import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public record InteractionContext(DeferrableInteractionEvent event) {
    public long guildId() { return event.getInteraction().getGuildId().orElseThrow(() -> new IllegalArgumentException("サーバー内で使用してください。")).asLong(); }
    public long userId() { return event.getInteraction().getUser().getId().asLong(); }
    public long channelId() { return event.getInteraction().getChannelId().asLong(); }
    public Mono<Member> member() { return Access.member(event.getClient(), guildId(), userId()); }
    public Mono<GuildMessageChannel> channel() { return event.getClient().getChannelById(Access.id(channelId())).ofType(GuildMessageChannel.class); }
    public String value(String id) {
        if (!(event instanceof ModalSubmitInteractionEvent modal)) throw new IllegalArgumentException("フォームが不正です。");
        return modal.getComponents(TextInput.class).stream().filter(input -> input.getCustomId().equals(id)).findFirst()
                .flatMap(TextInput::getValue).orElseThrow(() -> new IllegalArgumentException("入力を確認してください。"));
    }
    public Mono<Void> privateReply(MessageCreateSpec message) { return Replies.privateReply(event, message); }
    public Mono<Void> followup(MessageCreateSpec message, boolean privateMessage) { return Replies.followup(event, message, privateMessage).then(); }
    public Mono<Void> edit(MessageCreateSpec message) { return Replies.edit(event, message).then(); }
    public Mono<Void> defer(boolean privateMessage) { return event.deferReply().withEphemeral(privateMessage); }
    public Mono<Void> deferEdit() { return ((ComponentInteractionEvent) event).deferEdit(); }
    public boolean owner(String user) { return user.equals(Long.toUnsignedString(userId())); }
}
