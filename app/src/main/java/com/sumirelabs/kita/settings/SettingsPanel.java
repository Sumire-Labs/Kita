package com.sumirelabs.kita.settings;

import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.ui.Entities;
import com.sumirelabs.kita.discord.ui.Choices;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.MessageCreateSpec;

public final class SettingsPanel {
    private SettingsPanel() {}
    public static final List<String> PLATFORMS = List.of("x", "reddit", "tiktok", "twitch", "instagram", "youtube");

    public static MessageCreateSpec render(GuildSettings settings, long userId, String page) {
        var container = base(userId, page);
        var prefix = "settings:" + userId + ":";
        switch (page) {
            case "levels" -> container = com.sumirelabs.kita.levels.LevelsSettings.append(container, settings, userId, "chat");
            case "logshare" -> container = com.sumirelabs.kita.logshare.LogShareSettings.append(container, prefix, settings);
            case "previews" -> {
                var platforms = Choices.create(prefix + "platforms").setPlaceholder("有効にするSNS")
                        .setMinValues(0).setMaxValues(PLATFORMS.size());
                for (var platform : PLATFORMS) platforms.addOption(platform, platform);
                platforms.setDefaultValues(PLATFORMS.stream().filter(p -> settings.enabled("previews." + p)).toList());
                container = Ui.append(container, TextDisplay.of("### EmbedPlacer\n元の投稿を残してサイレント返信します。"),
                        toggle(prefix, "previews.enabled", settings), ActionRow.of(platforms.build()));
            }
            case "translation" -> container = Ui.append(container,
                    TextDisplay.of("### FlagTL\n国旗リアクションで翻訳します。原文の言語は自動検出。\n"
                            + "🇨🇳 簡体字 · 🇹🇼 繁体字 · 🇯🇵 日本語 · 🇺🇸/🇬🇧 英語\nDeepLキーは運営者の設定ファイルで管理。"),
                    toggle(prefix, "translation.enabled", settings));
            case "ticket" -> container = Ui.append(container,
                    TextDisplay.of("### Ticket\n非公開チャンネル・担当者・クローズ・履歴保存\nカテゴリ: "
                            + settings.value("ticket.category", "未設定") + "\n担当ロール: "
                            + settings.value("ticket.role", "未設定") + "\nパネル配置先: "
                            + settings.value("ticket.panel", "未設定")),
                    toggle(prefix, "ticket.enabled", settings),
                    ActionRow.of(Entities.create(prefix + "category", Entities.Target.CHANNEL)
                            .setChannelTypes(Channel.Type.GUILD_CATEGORY).setPlaceholder("チケット作成先カテゴリ").build()),
                    ActionRow.of(Entities.create(prefix + "role", Entities.Target.ROLE)
                            .setPlaceholder("担当ロール").build()),
                    ActionRow.of(Entities.create(prefix + "panel", Entities.Target.CHANNEL)
                            .setChannelTypes(Channel.Type.GUILD_TEXT).setPlaceholder("受付パネル配置先").build()),
                    ActionRow.of(Button.primary(prefix + "publish", "受付パネルを配置"),
                            Button.secondary(prefix + "template", "案内文・フォームを編集")));
            default -> container = Ui.append(container, TextDisplay.of("### 機能の状態\nEmbedPlacer: "
                    + state(settings.enabled("previews.enabled")) + "\nFlagTL: "
                    + state(settings.enabled("translation.enabled")) + "\nTicket: "
                    + state(settings.enabled("ticket.enabled")) + "\nLogShare: "
                    + state(settings.enabled("logshare.enabled")) + "\nLevels: "
                    + state(settings.enabled("levels.enabled")) + "\nカテゴリから設定を選択してください。"));
        }
        return Ui.message(List.of(container));
    }

    public static Container base(long userId, String page) {
        var navigation = Choices.create("settings:" + userId + ":page");
        navigation.addOption("概要", "home").addOption("EmbedPlacer", "previews").addOption("FlagTL", "translation")
                .addOption("Ticket", "ticket").addOption("LogShare", "logshare").addOption("Levels", "levels").setDefaultValues(page);
        return Container.of(TextDisplay.of("## Kita Settings\n設定はこのサーバーにだけ適用されます。"), ActionRow.of(navigation.build()));
    }

    private static ActionRow toggle(String prefix, String key, GuildSettings settings) {
        return ActionRow.of(Choices.create(prefix + key).addOption("有効", "true")
                .addOption("無効", "false").setDefaultValues(String.valueOf(settings.enabled(key))).build());
    }

    private static String state(boolean enabled) { return enabled ? "有効" : "無効"; }
}
