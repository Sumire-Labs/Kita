package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.discord.Forms;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.entity.channel.TextChannel;
import reactor.core.publisher.Mono;

public final class TicketListener {
    private final SettingsRepository settings;
    private final TicketService service;
    public TicketListener(SettingsRepository settings, TicketService service) { this.settings = settings; this.service = service; }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty() || !event.getCustomId().startsWith("ticket:")) return Mono.empty();
        var context = new InteractionContext(event);
        if (event.getCustomId().equals("ticket:open")) return context.member().then(settings.get(context.guildId())).flatMap(config -> {
            if (!config.enabled("ticket.enabled")) return context.privateReply(Ui.text("Ticket", "チケットは無効です。"));
            return event.presentModal(Forms.modal("ticket-create", "お問い合わせ", Forms.input("subject", "件名", 100),
                    Forms.paragraph("details", config.value("ticket.question", "お問い合わせ内容"), 2500)));
        }).onErrorResume(error -> context.privateReply(Ui.text("Ticket", "設定と権限を確認してください。")));
        if (event.getCustomId().equals("ticket:close")) return context.member().flatMap(member -> context.channel().ofType(TextChannel.class)
                .flatMap(channel -> service.authorize(member, channel))).then(event.presentModal(Forms.modal("ticket-close", "チケットのクローズ",
                        Forms.input("confirm", "確認のため CLOSE と入力", 5))))
                .onErrorResume(error -> context.privateReply(Ui.text("Ticket", "担当者権限とチケットの状態を確認してください。")));
        if (!event.getCustomId().equals("ticket:claim")) return Mono.empty();
        return context.defer(true).then(context.member().flatMap(member -> context.channel().ofType(TextChannel.class)
                .flatMap(channel -> service.claim(member, channel)))).then(context.followup(Ui.text("Ticket", "担当者として登録しました。"), true))
                .onErrorResume(error -> context.followup(Ui.text("Ticket", "担当者権限とチケットの状態を確認してください。"), true));
    }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty() || !event.getCustomId().startsWith("ticket-")) return Mono.empty();
        var context = new InteractionContext(event);
        return context.defer(true).then(Mono.defer(() -> {
            if (event.getCustomId().equals("ticket-create")) return context.member()
                    .flatMap(member -> service.open(member, context.value("subject"), context.value("details")))
                    .flatMap(channel -> context.followup(Ui.text("Ticket", "作成しました: <#" + channel.getId().asString() + ">"), true));
            if (!event.getCustomId().equals("ticket-close")) return Mono.empty();
            if (!context.value("confirm").equals("CLOSE")) return context.followup(Ui.text("Ticket", "クローズをキャンセルしました。"), true);
            return context.member().flatMap(member -> context.channel().ofType(TextChannel.class).flatMap(channel -> service.close(member, channel)))
                    .then(context.followup(Ui.text("Ticket", "チケットをクローズしました。"), true));
        })).onErrorResume(error -> context.followup(Ui.text("Ticket",
                error instanceof IllegalArgumentException ? error.getMessage() : "処理に失敗しました。設定と権限を確認してください。"), true));
    }
}
