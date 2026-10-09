package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsPanel;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.ui.Choices;
import com.sumirelabs.kita.discord.ui.Entities;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.MessageCreateSpec;

public final class LevelsSettings {
    private LevelsSettings() {}
    public static MessageCreateSpec render(GuildSettings settings, long owner, String page) {
        return Ui.message(List.of(append(SettingsPanel.base(owner, "levels"), settings, owner, page)));
    }
    public static Container append(Container base, GuildSettings settings, long owner, String page) {
        var config = LevelConfig.from(settings); String prefix = "levels-settings:" + owner + ":";
        var navigation = Choices.create(prefix + "page").addOption("Chat XP・共通設定", "chat")
                .addOption("VC XP", "voice").addOption("報酬ロール・通知", "rewards").setDefaultValues(page).build();
        var container = Ui.append(base, ActionRow.of(Button.primary(prefix + "toggle:enabled", "Levels: " + state(config.enabled()))),
                ActionRow.of(navigation));
        if (page.equals("rewards")) return LevelRewardSettings.append(container, settings, prefix);
        if (page.equals("voice")) return Ui.append(container,
                TextDisplay.of("### VC XP\n条件を満たした1分ごとに **" + config.voiceXp() + " XP**。\n"
                        + "Botを除く2人以上のVCが対象。AFKチャンネル・スピーカーミュート・除外設定は対象外です。\n"
                        + "VCを変更した場合は滞在時間を数え直します。再起動前の未確定の滞在時間は引き継ぎません。"),
                ActionRow.of(Button.secondary(prefix + "toggle:voice", "VC XP: " + state(config.voice())),
                        Button.secondary(prefix + "toggle:mutedVoice", "マイクミュート中: " + state(config.mutedVoice()))),
                ActionRow.of(Button.secondary(amounts(prefix, config), "XP量・間隔を変更")));
        var channels = Entities.create(prefix + "channels", Entities.Target.CHANNEL)
                .setChannelTypes(Channel.Type.GUILD_TEXT, Channel.Type.GUILD_FORUM, Channel.Type.GUILD_VOICE).setMinValues(0).setMaxValues(25)
                .setPlaceholder("XPを獲得しないチャンネル（Chat・VC共通）")
                .setDefaultValues(config.channels().stream().map(Entities::channel).toList());
        var roles = Entities.create(prefix + "roles", Entities.Target.ROLE)
                .setMinValues(0).setMaxValues(25).setPlaceholder("XPを獲得しないロール")
                .setDefaultValues(config.roles().stream().map(Entities::role).toList());
        return Ui.append(container, TextDisplay.of("### Chat XP\n**" + config.cooldown() + "秒に1回・" + config.chatXp() + " XP**。\n"
                        + "Bot・Webhook・k!コマンド・空の本文は対象外。同じ本文の連投は5分間加算しません。\n"
                        + "除外を指定しなければ全チャンネルが対象です。フォーラムの除外は配下にも適用します。"),
                ActionRow.of(Button.secondary(prefix + "toggle:chat", "Chat XP: " + state(config.chat())),
                        Button.secondary(amounts(prefix, config), "XP量・間隔を変更")), ActionRow.of(channels.build()), ActionRow.of(roles.build()));
    }
    private static String amounts(String prefix, LevelConfig config) {
        return prefix + "amounts:" + config.chatXp() + ":" + config.cooldown() + ":" + config.voiceXp();
    }
    private static String state(boolean enabled) { return enabled ? "有効" : "無効"; }
}
