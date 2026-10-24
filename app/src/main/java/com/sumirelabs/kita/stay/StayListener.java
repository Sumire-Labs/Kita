package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import reactor.core.publisher.Mono;

public final class StayListener {
    private final StayService stay;
    public StayListener(StayService stay) { this.stay = stay; }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty() || !event.getCustomId().startsWith("stay:")) return Mono.empty();
        var parts = event.getCustomId().split(":", 3);
        if (parts.length != 3) return Mono.empty();
        var context = new InteractionContext(event);
        if (parts[2].equals("duration")) return context.member().flatMap(StayVoice::require).flatMap(channel -> {
            stay.require(context.guildId(), channel, parts[1]); return event.presentModal(StayPanel.modal(parts[1]));
        }).onErrorResume(error -> context.privateReply(Ui.text("Stay", message(error))));
        return context.deferEdit().then(context.member().flatMap(StayVoice::require)
                .flatMap(channel -> stay.change(context.guildId(), channel, parts[1], null, parts[2].equals("stop"))))
                .onErrorResume(error -> context.followup(Ui.text("Stay", message(error)), true));
    }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty() || !event.getCustomId().startsWith("stay-time:")) return Mono.empty();
        var context = new InteractionContext(event);
        return context.defer(true).then(context.member().flatMap(StayVoice::require).flatMap(channel -> {
            var duration = StaySession.duration(context.value("hours").strip(), context.value("minutes").strip());
            return stay.change(context.guildId(), channel, event.getCustomId().substring(10), duration, false)
                    .then(context.followup(Ui.text("Stay", "今から " + duration.toHours() + " 時間 " + duration.toMinutesPart() + " 分後に退出します。"), true));
        })).onErrorResume(error -> context.followup(Ui.text("Stay", message(error)), true));
    }
    private static String message(Throwable error) {
        return error instanceof IllegalArgumentException ? error.getMessage() : "常駐の変更に失敗しました。時間をおいて再試行してください。";
    }
}
