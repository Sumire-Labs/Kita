package com.sumirelabs.kita.settings;

import com.sumirelabs.kita.discord.*;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.rest.util.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public final class SettingsListener {
    private final SettingsRepository repository;
    private final Function<Long, Mono<Void>> publish;
    public SettingsListener(SettingsRepository repository, Function<Long, Mono<Void>> publish) { this.repository = repository; this.publish = publish; }
    public Mono<Void> select(SelectMenuInteractionEvent event) {
        if (!event.getCustomId().startsWith("settings:")) return Mono.empty();
        String action = event.getCustomId().split(":", 3)[2]; var values = event.getValues();
        if (action.equals("logshare.channels")) return Replies.privateReply(event, Ui.text("Settings", "チャンネル設定は除外方式です。/settings を開き直してください。"));
        if (Set.of("category", "role", "panel", "logshare.excludedChannels", "logshare.roles").contains(action)) {
            String key = action.startsWith("logshare.") ? action : "ticket." + action;
            return update(event, action.startsWith("logshare.") ? "logshare" : "ticket", Map.of(key, String.join(",", values)));
        }
        var changes = new HashMap<String, String>(); String page;
        if (action.equals("page")) page = values.getFirst();
        else if (action.equals("music.crossfade")) {
            if (!values.getFirst().matches("[0-9]|1[0-2]")) return Mono.empty();
            changes.put(action, values.getFirst()); page = "music";
        } else if (action.equals("platforms")) {
            page = "previews";
            for (String platform : SettingsPanel.PLATFORMS) changes.put("previews." + platform, String.valueOf(values.contains(platform)));
        } else if (Set.of("previews.enabled", "translation.enabled", "ticket.enabled", "logshare.enabled", "logshare.auto").contains(action)) {
            if (!Set.of("true", "false").contains(values.getFirst())) return Mono.empty();
            changes.put(action, values.getFirst()); page = action.startsWith("previews") ? "previews" : action.startsWith("ticket") ? "ticket"
                    : action.startsWith("logshare") ? "logshare" : "translation";
        } else return Mono.empty();
        return update(event, page, changes);
    }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (!event.getCustomId().startsWith("settings:")) return Mono.empty();
        var context = new InteractionContext(event);
        if (!owner(event, context)) return context.privateReply(Ui.text("Settings", "このパネルを操作する権限がありません。"));
        String action = event.getCustomId().split(":", 3)[2];
        if (action.equals("template")) return context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD))
                .then(event.presentModal(Forms.modal("settings-template:" + context.userId(), "チケット設定",
                        Forms.paragraph("welcome", "受付案内文", 1500), Forms.input("question", "問い合わせ欄のラベル", 45))))
                .onErrorResume(error -> context.privateReply(Ui.text("Settings", "サーバー管理権限が必要です。")));
        if (action.equals("publish")) return context.defer(true).then(context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD)))
                .then(Mono.defer(() -> publish.apply(context.guildId()))).then(context.followup(Ui.text("Ticket", "受付パネルを配置しました。"), true))
                .onErrorResume(error -> context.followup(Ui.text("Ticket", "設定とBotの権限を確認してください。"), true));
        return Mono.empty();
    }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (!event.getCustomId().startsWith("settings-template:")) return Mono.empty();
        var context = new InteractionContext(event);
        if (event.getInteraction().getGuildId().isEmpty() || !context.owner(event.getCustomId().split(":")[1])) {
            return context.privateReply(Ui.text("Settings", "この設定を操作する権限がありません。"));
        }
        return context.defer(true).then(context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD)))
                .then(Mono.defer(() -> repository.update(context.guildId(), Map.of("ticket.welcome", context.value("welcome"), "ticket.question", context.value("question")))))
                .then(context.followup(Ui.text("Settings", "保存しました。受付パネルを再配置すると反映されます。"), true))
                .onErrorResume(error -> context.followup(Ui.text("Settings", "保存に失敗しました。権限を確認してください。"), true));
    }
    private Mono<Void> update(ComponentInteractionEvent event, String page, Map<String, String> changes) {
        var context = new InteractionContext(event);
        if (!owner(event, context)) return context.privateReply(Ui.text("Settings", "このパネルを操作する権限がありません。"));
        return context.deferEdit().then(context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD)))
                .then(Mono.defer(() -> changes.isEmpty() ? repository.get(context.guildId()) : repository.update(context.guildId(), changes)))
                .flatMap(settings -> context.edit(SettingsPanel.render(settings, context.userId(), page)))
                .onErrorResume(error -> context.followup(Ui.text("Settings", "保存できませんでした。権限と設定を確認してください。"), true));
    }
    private static boolean owner(ComponentInteractionEvent event, InteractionContext context) {
        return event.getInteraction().getGuildId().isPresent() && context.owner(event.getCustomId().split(":", 3)[1]);
    }
}
