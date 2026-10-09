package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.ui.Entities;
import com.sumirelabs.kita.discord.ui.Choices;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.channel.Channel;

public final class LogShareSettings {
    private LogShareSettings() {}
    public static Container append(Container container, String prefix, GuildSettings settings) {
        var channels = Entities.create(prefix + "logshare.excludedChannels", Entities.Target.CHANNEL)
                .setChannelTypes(Channel.Type.GUILD_TEXT, Channel.Type.GUILD_FORUM).setMinValues(0).setMaxValues(25).setPlaceholder("LogShareを使えないチャンネル（除外）");
        channels.setDefaultValues(LogSharePolicy.ids(settings, "logshare.excludedChannels").stream().map(Entities::channel).toList());
        var roles = Entities.create(prefix + "logshare.roles", Entities.Target.ROLE)
                .setMinValues(0).setMaxValues(25).setPlaceholder("他の人のログを共有できる担当ロール");
        roles.setDefaultValues(LogSharePolicy.ids(settings, "logshare.roles").stream().map(Entities::role).toList());
        return Ui.append(container, TextDisplay.of("### LogShare\nログを外部サイト mclo.gs に共有して、リンクをサイレント返信します。\n"
                        + "除外指定がなければ全チャンネルで利用できます。フォーラムの除外は配下のスレッドにも適用します。\n"
                        + "自動: 除外されていない場所の判定できた .log / .txt 添付のみ。\n"
                        + "手動: メッセージメニュー『mclo.gsで共有』または 📋。本文も対応。\n"
                        + "手動共有は投稿者本人・サーバー管理者・担当ロールのみ利用できます。"),
                toggle(prefix, "logshare.enabled", "機能全体", settings), toggle(prefix, "logshare.auto", "添付ログの自動共有", settings),
                ActionRow.of(channels.build()), ActionRow.of(roles.build()));
    }
    private static ActionRow toggle(String prefix, String key, String label, GuildSettings settings) {
        return ActionRow.of(Choices.create(prefix + key).addOption(label + ": 有効", "true")
                .addOption(label + ": 無効", "false").setDefaultValues(String.valueOf(settings.enabled(key))).build());
    }
}
