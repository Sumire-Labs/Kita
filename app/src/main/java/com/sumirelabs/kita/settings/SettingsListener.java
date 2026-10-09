package com.sumirelabs.kita.settings;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class SettingsListener extends ListenerAdapter {
    private final SettingsRepository repository;
    private final WorkExecutor worker;
    private final LongConsumer publish;
    public SettingsListener(SettingsRepository repository, WorkExecutor worker, LongConsumer publish) {
        this.repository = repository; this.worker = worker; this.publish = publish;
    }

    private boolean authorized(ComponentInteraction event) {
        if (!event.getComponentId().startsWith("settings:")) return false;
        var parts = event.getComponentId().split(":", 3);
        if (!event.isFromGuild() || !parts[1].equals(event.getUser().getId())
                || event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(Ui.text("Settings", "このパネルを操作する権限がありません。")).setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!authorized(event)) return;
        String action = event.getComponentId().split(":", 3)[2];
        String page = action.startsWith("previews") || action.equals("platforms") ? "previews" : "translation";
        var changes = new HashMap<String, String>();
        if (action.equals("page")) page = event.getValues().getFirst();
        else if (action.equals("platforms")) {
            for (var platform : SettingsPanel.PLATFORMS) changes.put("previews." + platform,
                    String.valueOf(event.getValues().contains(platform)));
        } else if (java.util.Set.of("previews.enabled", "translation.enabled", "ticket.enabled", "logshare.enabled", "logshare.auto").contains(action)) {
            changes.put(action, event.getValues().getFirst());
            if (action.startsWith("ticket")) page = "ticket";
            if (action.startsWith("logshare")) page = "logshare";
        } else return;
        update(event, page, changes);
    }

    @Override public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!authorized(event)) return;
        String action = event.getComponentId().split(":", 3)[2];
        if (java.util.Set.of("logshare.channels", "logshare.roles").contains(action)) {
            update(event, "logshare", Map.of(action, event.getValues().stream().map(value -> value.getId())
                    .collect(java.util.stream.Collectors.joining(","))));
            return;
        }
        if (!java.util.Set.of("category", "role", "panel").contains(action)) return;
        update(event, "ticket", Map.of("ticket." + action, event.getValues().getFirst().getId()));
    }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!authorized(event)) return;
        var action = event.getComponentId().split(":", 3)[2];
        if (action.equals("template")) {
            event.replyModal(Modal.create("settings-template:" + event.getUser().getId(), "チケット設定")
                    .addComponents(Label.of("受付案内文", TextInput.create("welcome", TextInputStyle.PARAGRAPH)
                            .setMaxLength(1500).build()),
                            Label.of("問い合わせ欄のラベル", TextInput.create("question", TextInputStyle.SHORT)
                                    .setMaxLength(45).build())).build()).queue();
        } else if (action.equals("publish")) {
            event.deferReply(true).queue(hook -> {
                if (!worker.submit(() -> {
                    try { publish.accept(event.getGuild().getIdLong()); hook.sendMessage(Ui.text("Ticket", "受付パネルを配置しました。")).queue(); }
                    catch (Exception error) { hook.sendMessage(Ui.text("Ticket", "設定とBotの権限を確認してください。")).queue(); }
                })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).queue();
            });
        }
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("settings-template:" + event.getUser().getId())) return;
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(Ui.text("Settings", "サーバー管理権限が必要です。")).setEphemeral(true).queue(); return;
        }
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                repository.update(event.getGuild().getIdLong(), Map.of("ticket.welcome", event.getValue("welcome").getAsString(),
                        "ticket.question", event.getValue("question").getAsString()));
                hook.sendMessage(Ui.text("Settings", "保存しました。受付パネルを再配置すると案内文が反映されます。")).queue();
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).queue();
        });
    }

    private void update(ComponentInteraction event, String page, Map<String, String> changes) {
        event.deferEdit().queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                        hook.sendMessage(Ui.text("Settings", "サーバー管理権限が必要です。")).setEphemeral(true).queue(); return;
                    }
                    var settings = changes.isEmpty() ? repository.get(event.getGuild().getIdLong())
                            : repository.update(event.getGuild().getIdLong(), changes);
                    hook.editOriginal(Ui.edit(SettingsPanel.render(settings, event.getUser().getIdLong(), page))).queue();
                } catch (Exception error) { hook.sendMessage(Ui.text("Settings", "保存に失敗しました。")).setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).setEphemeral(true).queue();
        });
    }
}
