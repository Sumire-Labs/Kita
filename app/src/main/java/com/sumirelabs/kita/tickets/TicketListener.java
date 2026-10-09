package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;

public final class TicketListener extends ListenerAdapter {
    private final SettingsRepository settings;
    private final TicketService service;
    private final WorkExecutor worker;
    public TicketListener(SettingsRepository settings, TicketService service, WorkExecutor worker) {
        this.settings = settings; this.service = service; this.worker = worker;
    }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("ticket:") || !event.isFromGuild()) return;
        if (event.getComponentId().equals("ticket:open")) {
            if (!worker.submit(() -> {
                var config = settings.get(event.getGuild().getIdLong());
                if (!config.enabled("ticket.enabled")) {
                    event.reply(Ui.text("Ticket", "チケットは無効です。")).setEphemeral(true).queue(); return;
                }
                event.replyModal(Modal.create("ticket-create", "お問い合わせ")
                        .addComponents(Label.of("件名", TextInput.create("subject", TextInputStyle.SHORT).setMaxLength(100).build()),
                                Label.of(config.value("ticket.question", "お問い合わせ内容"), TextInput.create("details", TextInputStyle.PARAGRAPH)
                                        .setMaxLength(2500).build())).build()).queue();
            })) event.reply(Ui.text("Kita", "現在混み合っています。")).setEphemeral(true).queue();
        } else if (event.getComponentId().equals("ticket:close")) {
            event.replyModal(Modal.create("ticket-close", "チケットのクローズ")
                    .addComponents(Label.of("確認のため CLOSE と入力", TextInput.create("confirm", TextInputStyle.SHORT)
                            .setMaxLength(5).build())).build()).queue();
        } else if (event.getComponentId().equals("ticket:claim")) {
            event.deferReply(true).queue(hook -> {
                if (!worker.submit(() -> {
                    try { service.claim(event.getMember(), event.getChannel().asTextChannel());
                        hook.sendMessage(Ui.text("Ticket", "担当者として登録しました。")).queue(); }
                    catch (Exception error) { hook.sendMessage(Ui.text("Ticket", "担当者権限とチケットの状態を確認してください。")).queue(); }
                })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).queue();
            });
        }
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.isFromGuild() || !event.getModalId().startsWith("ticket-")) return;
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    if (event.getModalId().equals("ticket-create")) {
                        var channel = service.open(event.getMember(), event.getValue("subject").getAsString(),
                                event.getValue("details").getAsString());
                        hook.sendMessage(Ui.text("Ticket", "作成しました: <#" + channel.getId() + ">")).queue();
                    } else if (event.getModalId().equals("ticket-close")) {
                        if (!event.getValue("confirm").getAsString().equals("CLOSE")) {
                            hook.sendMessage(Ui.text("Ticket", "クローズをキャンセルしました。")).queue(); return;
                        }
                        service.close(event.getMember(), event.getChannel().asTextChannel());
                        hook.sendMessage(Ui.text("Ticket", "チケットをクローズしました。")).queue();
                    }
                } catch (IllegalArgumentException error) { hook.sendMessage(Ui.text("Ticket", error.getMessage())).queue(); }
                catch (Exception error) { hook.sendMessage(Ui.text("Ticket", "処理に失敗しました。設定と権限を確認してください。")).queue(); }
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).queue();
        });
    }
}
