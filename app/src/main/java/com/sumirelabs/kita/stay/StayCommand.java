package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import com.sumirelabs.kita.discord.Ui;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public final class StayCommand implements Command {
    private final StayService stay;
    public StayCommand(StayService stay) { this.stay = stay; }
    @Override public ApplicationCommandRequest definition() { return Commands.slash("stay", "ボイスチャンネルへの常駐を開始・終了"); }
    @Override public Mono<Void> execute(CommandContext context) {
        return context.member().flatMap(StayVoice::require).flatMap(channel -> stay.toggle(context.guildId(), channel))
                .flatMap(view -> context.replyMessage(StayPanel.render(view)).flatMap(message -> {
                    if (!view.active()) return Mono.empty();
                    return stay.panel(context.guildId(), view.token(), message.getChannelId().asLong(), message.getId().asLong())
                            .onErrorResume(IllegalArgumentException.class, error ->
                                    message.edit(Ui.edit(Ui.text("Stay", "この常駐は終了しました。"))).then());
                }));
    }
}
