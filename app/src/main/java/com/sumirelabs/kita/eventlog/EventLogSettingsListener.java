package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.*;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.component.Label;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.gateway.intent.Intent;
import discord4j.rest.util.Permission;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public final class EventLogSettingsListener {
    private final SettingsRepository repository;
    private final EventLogService service;
    public EventLogSettingsListener(SettingsRepository repository, EventLogService service) { this.repository = repository; this.service = service; }
    public Mono<Void> select(SelectMenuInteractionEvent event) {
        if (!event.getCustomId().startsWith("eventlog-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return denied(context);
        var values = event.getValues(); String action = parts[2];
        if (action.equals("page")) return update(context, values.getFirst(), old -> Map.of(), Mono.empty());
        if (action.equals("channel")) {
            String key = parts[3].equals("default") ? "eventlog.channel" : "eventlog.channel." + LogAction.Category.valueOf(parts[3]);
            String page = parts[3].equals("default") ? "home" : parts[3];
            Mono<Void> validation = values.isEmpty() ? Mono.empty() : event.getClient().getChannelById(discord4j.common.util.Snowflake.of(values.getFirst()))
                    .ofType(TextChannel.class).filter(channel -> channel.getGuildId().asLong() == context.guildId())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("このサーバーのテキストチャンネルを選択してください。")))
                    .flatMap(channel -> context.member().flatMap(member -> Access.channel(member, channel, Permission.VIEW_CHANNEL))
                            .then(Access.member(event.getClient(), context.guildId(), event.getClient().getSelfId().asLong()))
                            .flatMap(bot -> Access.channel(bot, channel, Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES, Permission.ATTACH_FILES)));
            return update(context, page, old -> Map.of(key, values.isEmpty() ? "" : values.getFirst()), validation);
        }
        if (action.equals("actions")) {
            var category = LogAction.Category.valueOf(parts[3]);
            var allowed = Arrays.stream(LogAction.values()).filter(option -> option.category() == category).toList();
            if (!allowed.stream().map(Enum::name).toList().containsAll(values)) return Mono.empty();
            return update(context, category.name(), old -> {
                var changes = new java.util.HashMap<String, String>();
                for (var option : allowed) changes.put("eventlog.action." + option, String.valueOf(values.contains(option.name())));
                return changes;
            }, Mono.empty());
        }
        if (action.equals("exclude")) return update(context, "storage", old -> Map.of("eventlog.exclude.channels", String.join(",", values)), Mono.empty());
        String value = values.getFirst();
        if (action.equals("hours") && Set.of("1", "6", "24").contains(value) || action.equals("capacity") && Set.of("8", "32", "64", "128").contains(value)) {
            return update(context, "storage", old -> Map.of("eventlog." + action, value), Mono.empty());
        }
        return Mono.empty();
    }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (!event.getCustomId().startsWith("eventlog-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return denied(context);
        if (parts[2].equals("toggle") && Set.of("enabled", "bots", "bodies", "silent").contains(parts[3])) {
            return update(context, parts[3].equals("bodies") ? "storage" : "home", old -> Map.of("eventlog." + parts[3],
                    String.valueOf(!Boolean.parseBoolean(old.value("eventlog." + parts[3], Set.of("bodies", "silent").contains(parts[3]) ? "true" : "false")))), Mono.empty());
        }
        if (parts[2].equals("preview")) return context.defer(true).then(authorize(context)).then(context.followup(
                Ui.text("EventLog · Preview", "これは表示確認用です。実際の操作ログではありません。\n対象・実行者・変更前後・日時をここに表示します。"), true))
                .onErrorResume(error -> context.followup(Ui.text("EventLog", "サーバー管理権限が必要です。"), true));
        if (!parts[2].equals("users")) return Mono.empty();
        return authorize(context).then(repository.get(context.guildId())).flatMap(settings -> event.presentModal(Forms.modal(
                "eventlog-users:" + context.userId(), "除外ユーザー", Label.of("ユーザーID／メンション（空欄で解除、カンマ区切り）",
                        TextInput.paragraph("users", 0, 1000).required(false).prefilled(settings.value("eventlog.exclude.users", ""))))))
                .onErrorResume(error -> denied(context));
    }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (!event.getCustomId().startsWith("eventlog-users:")) return Mono.empty();
        var context = new InteractionContext(event);
        if (!owner(context, event.getCustomId().split(":"))) return denied(context);
        return context.defer(true).then(authorize(context)).then(Mono.defer(() -> {
            String value = event.getComponents(TextInput.class).stream().filter(input -> input.getCustomId().equals("users"))
                    .findFirst().flatMap(TextInput::getValue).orElse("");
            return repository.update(context.guildId(), Map.of("eventlog.exclude.users", users(value)));
        })).flatMap(updated -> service.purge(context.guildId()).then(status(context)).flatMap(status ->
                context.followup(EventLogSettings.render(updated, context.userId(), "storage", status), true)))
                .onErrorResume(error -> context.followup(Ui.text("EventLog", error instanceof IllegalArgumentException ? error.getMessage() : "保存できませんでした。"), true));
    }
    private Mono<Void> update(InteractionContext context, String page, Function<GuildSettings, Map<String, String>> changes, Mono<Void> validation) {
        return context.deferEdit().then(authorize(context)).then(validation).then(repository.get(context.guildId())).flatMap(old -> {
            boolean mutation = !changes.apply(old).isEmpty();
            return (mutation ? repository.modify(context.guildId(), changes) : Mono.just(old))
                    .flatMap(updated -> (mutation ? service.purge(context.guildId()) : Mono.<Void>empty()).then(status(context))
                            .flatMap(status -> context.edit(EventLogSettings.render(updated, context.userId(), page, status))));
        }).onErrorResume(error -> context.followup(Ui.text("EventLog", error instanceof IllegalArgumentException ? error.getMessage() : "設定と権限を確認してください。"), true));
    }
    private static Mono<Void> authorize(InteractionContext context) { return context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD)); }
    private static Mono<String> status(InteractionContext context) {
        boolean members = context.event().getClient().getGatewayResources().getIntents().contains(Intent.GUILD_MEMBERS);
        return Access.member(context.event().getClient(), context.guildId(), context.event().getClient().getSelfId().asLong())
                .flatMap(bot -> Access.has(bot, Permission.VIEW_AUDIT_LOG)).map(audit -> "参加・退出：" + (members ? "利用可能" : "Members Intent無効")
                        + " · 監査ログ：" + (audit ? "閲覧可能" : "権限なし（管理操作の詳細を取得不可）"));
    }
    static String users(String value) {
        var values = Arrays.stream(value.replaceAll("[<@!>]", "").split("[,、\\s]+")).filter(part -> !part.isBlank()).distinct().toList();
        if (values.size() > 25 || values.stream().anyMatch(part -> !part.matches("[0-9]{1,19}"))) throw new IllegalArgumentException("ユーザーIDを25件以内で入力してください。");
        for (var part : values) try { if (Long.parseLong(part) <= 0) throw new NumberFormatException(); } catch (NumberFormatException error) { throw new IllegalArgumentException("ユーザーIDが不正です。"); }
        return String.join(",", values);
    }
    private static boolean owner(InteractionContext context, String[] parts) { return context.event().getInteraction().getGuildId().isPresent() && parts.length >= 2 && context.owner(parts[1]); }
    private static Mono<Void> denied(InteractionContext context) { return context.privateReply(Ui.text("EventLog", "このパネルを操作する権限がありません。")); }
}