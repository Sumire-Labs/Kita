package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.ui.Choices;
import com.sumirelabs.kita.discord.ui.Entities;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsPanel;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.MessageCreateSpec;
import java.util.Arrays;
import java.util.List;

public final class EventLogSettings {
    private EventLogSettings() {}
    public static MessageCreateSpec render(GuildSettings settings, long owner, String page, String status) {
        return Ui.message(List.of(append(SettingsPanel.base(owner, "eventlog"), settings, owner, page, status)));
    }
    public static Container append(Container base, GuildSettings settings, long owner, String page, String status) {
        var config = new EventLogConfig(settings); String prefix = "eventlog-settings:" + owner + ":";
        var navigation = Choices.create(prefix + "page").addOption("共通設定", "home").addOption("保存・除外", "storage");
        for (var category : LogAction.Category.values()) navigation.addOption(category.name(), category.name());
        var result = Ui.append(base, TextDisplay.of("### EventLog\n" + status), ActionRow.of(navigation.setDefaultValues(page).build()));
        if (page.equals("home")) {
            return Ui.append(result, TextDisplay.of("操作をComponents V2で記録します。本文が転記されるため、出力先の閲覧権限を確認してください。\n"
                            + "ログ出力先とKita自身の操作は記録しません。Bot・Webhookは初期状態では除外します。"),
                    ActionRow.of(Button.primary(prefix + "toggle:enabled", "EventLog：" + (config.enabled() ? "有効" : "無効"))),
                    ActionRow.of(channel(prefix + "channel:default", settings.snowflake("eventlog.channel"), "共通のログ出力先")),
                    ActionRow.of(Button.secondary(prefix + "toggle:bots", "Bot・Webhook：" + (config.bots() ? "含める" : "除外")),
                            Button.secondary(prefix + "toggle:silent", "通知：" + (config.silent() ? "サイレント" : "通常"))),
                    ActionRow.of(Button.secondary(prefix + "preview", "表示サンプル（自分だけ）")));
        }
        if (page.equals("storage")) {
            var hours = Choices.create(prefix + "hours");
            for (int value : List.of(1, 6, 24)) hours.addOption(value + "時間", String.valueOf(value));
            var capacity = Choices.create(prefix + "capacity");
            for (int value : List.of(8, 32, 64, 128)) capacity.addOption(value + "MiBまで", String.valueOf(value));
            var excluded = Entities.create(prefix + "exclude", Entities.Target.CHANNEL).setChannelTypes(Channel.Type.GUILD_CATEGORY,
                            Channel.Type.GUILD_TEXT, Channel.Type.GUILD_FORUM, Channel.Type.GUILD_VOICE).setMinValues(0).setMaxValues(25)
                    .setPlaceholder("記録・保存しないチャンネル／カテゴリ")
                    .setDefaultValues(config.exclusions("channels").stream().map(Entities::channel).toList());
            return Ui.append(result, TextDisplay.of("本文は対象チャンネルだけMariaDBへ保存します。初期値は6時間・64MiBです。\n"
                            + "容量制限や未受信により本文を取得できない場合は明示します。添付本体は保存しません。\n"
                            + "設定変更時はこのサーバーの保存本文と送信待ちを削除し、新しい条件で受信を再開します。"),
                    ActionRow.of(hours.setDefaultValues(String.valueOf(config.hours())).setPlaceholder("本文の保存期間").build()),
                    ActionRow.of(capacity.setDefaultValues(String.valueOf(config.snapshotLimit() / 1024 / 1024)).setPlaceholder("このサーバーの保存容量").build()),
                    ActionRow.of(excluded.build()), ActionRow.of(Button.secondary(prefix + "toggle:bodies", "本文・添付情報：" + (config.bodies() ? "保存・表示" : "保存しない")),
                            Button.secondary(prefix + "users", "除外ユーザーを編集")));
        }
        LogAction.Category category;
        try { category = LogAction.Category.valueOf(page); } catch (IllegalArgumentException error) { return result; }
        var actions = Choices.create(prefix + "actions:" + category).setMinValues(0);
        var options = Arrays.stream(LogAction.values()).filter(action -> action.category() == category).toList();
        for (var action : options) actions.addOption(action.label(), action.name());
        actions.setMaxValues(options.size()).setDefaultValues(options.stream().filter(config::selected).map(Enum::name).toList());
        return Ui.append(result, TextDisplay.of("### " + category + "\n個別出力先が未指定の場合は共通の出力先を使います。\n"
                        + "Roles・Channels・Server・管理操作の詳細は監査ログ閲覧権限が必要です。参加・退出にはServer Members Intentが必要です。"),
                ActionRow.of(channel(prefix + "channel:" + category, settings.snowflake("eventlog.channel." + category), "カテゴリ別の出力先（空欄で共通）")),
                ActionRow.of(actions.build()));
    }
    private static SelectMenu channel(String id, long selected, String placeholder) {
        var menu = Entities.create(id, Entities.Target.CHANNEL).setChannelTypes(Channel.Type.GUILD_TEXT).setMinValues(0).setMaxValues(1).setPlaceholder(placeholder);
        if (selected != 0) menu.setDefaultValues(Entities.channel(selected));
        return menu.build();
    }
}