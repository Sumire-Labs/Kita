package com.sumirelabs.kita.discord;

import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.FollowupMessageRequest;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.MultipartRequest;
import reactor.core.publisher.Mono;

public final class Replies {
    private Replies() {}
    public static Mono<Message> followup(DeferrableInteractionEvent event, MessageCreateSpec message, boolean ephemeral) {
        return event.getInteractionResponse().createFollowupMessage(followupRequest(message, ephemeral)).map(data -> new Message(event.getClient(), data));
    }
    static MultipartRequest<FollowupMessageRequest> followupRequest(MessageCreateSpec message, boolean ephemeral) {
        var multipart = InteractionFollowupCreateSpec.builder().components(message.components()).files(message.files())
                .allowedMentions(AllowedMentions.builder().repliedUser(false).build()).ephemeral(ephemeral).build().asRequest();
        // D4J's high-level followup spec does not expose SUPPRESS_NOTIFICATIONS yet.
        var body = FollowupMessageRequest.builder().from(java.util.Objects.requireNonNull(multipart.getJsonPayload()))
                .flags(Possible.of(Message.Flag.IS_COMPONENTS_V2.getFlag() | Message.Flag.SUPPRESS_NOTIFICATIONS.getFlag()
                        | (ephemeral ? Message.Flag.EPHEMERAL.getFlag() : 0))).build();
        return multipart.withRequest(body);
    }
    public static Mono<Void> privateReply(DeferrableInteractionEvent event, MessageCreateSpec message) {
        return event.reply(callback(message));
    }
    static InteractionApplicationCommandCallbackSpec callback(MessageCreateSpec message) {
        return InteractionApplicationCommandCallbackSpec.builder().components(message.components())
                .files(message.files()).allowedMentions(AllowedMentions.builder().repliedUser(false).build()).ephemeral(true).build();
    }
    public static Mono<Message> edit(DeferrableInteractionEvent event, MessageCreateSpec message) {
        return event.editReply(InteractionReplyEditSpec.builder().components(Possible.of(message.components().toOptional()))
                .allowedMentions(Possible.of(java.util.Optional.of(AllowedMentions.builder().repliedUser(false).build()))).build());
    }
    public static Mono<Message> reference(Message original, MessageCreateSpec message) {
        return original.getChannel().flatMap(channel -> channel.createMessage(message.withMessageReference(MessageReferenceData.builder()
                .messageId(original.getId().asLong()).channelId(original.getChannelId().asLong()).failIfNotExists(true).build())));
    }
}
