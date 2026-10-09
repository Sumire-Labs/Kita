package com.sumirelabs.kita.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import java.util.List;
import reactor.core.publisher.Mono;

public record CommandContext(GatewayDiscordClient client, long guildId, long channelId, User user, List<String> arguments,
                             ChatInputInteractionEvent slash, Message source, boolean privateReply, int shardId) {
    public Mono<Guild> guild() { return client.getGuildById(Access.id(guildId)); }
    public Mono<Member> member() { return Access.member(client, guildId, user.getId().asLong()); }
    public Mono<GuildMessageChannel> channel() { return client.getChannelById(Access.id(channelId)).ofType(GuildMessageChannel.class); }
    public String option(String name, String fallback) {
        return slash == null ? arguments.isEmpty() ? fallback : String.join(" ", arguments) : slash.getOptionAsString(name).orElse(fallback);
    }
    public Mono<User> target(String name) {
        if (slash != null) return slash.getOptionAsUser(name).defaultIfEmpty(user);
        if (arguments.isEmpty()) return Mono.just(user);
        return client.getUserById(discord4j.common.util.Snowflake.of(arguments.getFirst().replaceAll("[<@!>]", "")));
    }
    public Mono<Message> replyMessage(MessageCreateSpec message) {
        return slash == null ? Replies.reference(source, message) : Replies.followup(slash, message, privateReply);
    }
    public Mono<Void> reply(MessageCreateSpec message) { return replyMessage(message).then(); }
    public Mono<Void> reply(String title, String body) { return reply(Ui.text(title, body)); }
}
