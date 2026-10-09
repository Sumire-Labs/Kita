package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;

public final class LogShareSettings {
    private LogShareSettings() {}
    public static Container append(Container container, String prefix, GuildSettings settings) {
        var channels = EntitySelectMenu.create(prefix + "logshare.channels", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.FORUM).setMinValues(0).setMaxValues(25).setPlaceholder("添付ログを自動共有するチャンネル");
        channels.setDefaultValues(LogSharePolicy.ids(settings, "logshare.channels").stream().map(EntitySelectMenu.DefaultValue::channel).toList());
        var roles = EntitySelectMenu.create(prefix + "logshare.roles", EntitySelectMenu.SelectTarget.ROLE)
                .setMinValues(0).setMaxValues(25).setPlaceholder("他の人のログを共有できる担当ロール");
        roles.setDefaultValues(LogSharePolicy.ids(settings, "logshare.roles").stream().map(EntitySelectMenu.DefaultValue::role).toList());
        return Ui.append(container, TextDisplay.of("### LogShare\nログを外部サイト mclo.gs に共有して、リンクをサイレント返信します。\n"
                        + "自動: 指定チャンネル・フォーラム内の判定できた .log / .txt 添付のみ。\n"
                        + "手動: メッセージメニュー『mclo.gsで共有』または 📋。本文も対応。\n"
                        + "手動共有は投稿者本人・サーバー管理者・担当ロールのみ利用できます。"),
                toggle(prefix, "logshare.enabled", "機能全体", settings), toggle(prefix, "logshare.auto", "添付ログの自動共有", settings),
                ActionRow.of(channels.build()), ActionRow.of(roles.build()));
    }
    private static ActionRow toggle(String prefix, String key, String label, GuildSettings settings) {
        return ActionRow.of(StringSelectMenu.create(prefix + key).addOption(label + ": 有効", "true")
                .addOption(label + ": 無効", "false").setDefaultValues(String.valueOf(settings.enabled(key))).build());
    }
}
