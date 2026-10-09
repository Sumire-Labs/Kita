package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsPanel;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class LevelsSettings {
    private LevelsSettings() {}
    public static MessageCreateData render(GuildSettings settings, long owner, String page) {
        return Ui.message(List.of(append(SettingsPanel.base(owner, "levels"), settings, owner, page)));
    }
    public static Container append(Container base, GuildSettings settings, long owner, String page) {
        var config = LevelConfig.from(settings); String prefix = "levels-settings:" + owner + ":";
        var navigation = StringSelectMenu.create(prefix + "page").addOption("Chat XP・共通設定", "chat")
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
        var channels = EntitySelectMenu.create(prefix + "channels", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.FORUM, ChannelType.VOICE).setMinValues(0).setMaxValues(25)
                .setPlaceholder("XPを獲得しないチャンネル（Chat・VC共通）")
                .setDefaultValues(config.channels().stream().map(EntitySelectMenu.DefaultValue::channel).toList());
        var roles = EntitySelectMenu.create(prefix + "roles", EntitySelectMenu.SelectTarget.ROLE)
                .setMinValues(0).setMaxValues(25).setPlaceholder("XPを獲得しないロール")
                .setDefaultValues(config.roles().stream().map(EntitySelectMenu.DefaultValue::role).toList());
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
