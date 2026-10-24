package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.CommandContext;
import reactor.core.publisher.Mono;

final class PlayerPanels {
    private PlayerPanels() {}
    static Mono<Void> publish(CommandContext context, MusicService music) {
        return music.voiceOperation(context.guildId(), () -> context.replyMessage(PlayerPanel.render(music, context.guildId()))
                .flatMap(message -> {
                    var previous = music.session(context.guildId()).replacePanel(message.getChannelId().asLong(), message.getId().asLong());
                    if (previous.messageId() == 0) return Mono.empty();
                    return context.client().getMessageById(Access.id(previous.channelId()), Access.id(previous.messageId())).flatMap(old -> old.delete())
                            .onErrorResume(error -> Mono.empty());
                }));
    }
}
