package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.ui.Choices;
import com.sumirelabs.kita.discord.ui.Entities;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsPanel;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.MessageCreateSpec;
import java.util.List;

public final class EarthquakeSettings {
    private EarthquakeSettings() {}
    public static MessageCreateSpec render(GuildSettings settings, long owner, String page, String status) {
        return Ui.message(List.of(append(SettingsPanel.base(owner, "earthquake"), settings, owner, page, status)));
    }
    public static Container append(Container base, GuildSettings settings, long owner, String page, String status) {
        var config = EarthquakeConfig.from(settings); String prefix = "earthquake-settings:" + owner + ":";
        var navigation = Choices.create(prefix + "page").addOption("受信・通知先", "general").addOption("地域・観測震度", "filters")
                .addOption("通知音・メンション・続報", "delivery").setDefaultValues(page).build();
        var container = Ui.append(base, TextDisplay.of("### Seismo\n取得元：P2P地震情報\n" + status
                        + "\nEEW警報・観測地震情報・津波情報に対応。EEW予報は現在未対応です。"),
                ActionRow.of(navigation), ActionRow.of(Button.primary(prefix + "toggle", "通知：" + (config.enabled() ? "有効" : "無効"))));
        if (page.equals("filters")) {
            var minimum = Choices.create(prefix + "minimum").setPlaceholder("観測地震情報の最小震度");
            for (int scale : SeismicIntensity.SCALES) minimum.addOption(scale == 0 ? "すべて（震度不明を含む）" : "震度" + SeismicIntensity.label(scale) + "以上", String.valueOf(scale));
            minimum.setDefaultValues(String.valueOf(config.minimum()));
            return Ui.append(container, TextDisplay.of("地域：**" + summary(config.regions()) + "**\n津波予報区：**" + summary(config.tsunamiRegions())
                            + "**\n最小震度は観測地震情報に適用します。EEW警報・津波情報は震度で除外しません。\n"
                            + "地域を指定した場合、その地域で観測された震度を使います。震央の場所では判定しません。"),
                    ActionRow.of(minimum.build()), ActionRow.of(Button.secondary(prefix + "regions", "地域キーワードを編集")));
        }
        if (page.equals("delivery")) {
            var sound = Choices.create(prefix + "sound").addOption("すべて通常通知", "normal").addOption("すべてサイレント", "silent")
                    .setDefaultValues(config.silent() ? "silent" : "normal");
            var updates = Choices.create(prefix + "updates").addOption("続報も新しいメッセージで通知", "new")
                    .addOption("続報は元のメッセージを更新", "edit").addOption("内容が同じ続報は編集、変化時は通知", "compact")
                    .setDefaultValues(config.updates());
            var role = Entities.create(prefix + "role", Entities.Target.ROLE).setMinValues(0).setMaxValues(1).setPlaceholder("メンションするロール（任意）");
            if (config.role() != 0) role.setDefaultValues(Entities.role(Long.toUnsignedString(config.role())));
            return Ui.append(container, TextDisplay.of("通知音の初期値は通常通知です。Discord側の通知設定にも依存します。\n"
                            + "メンションは指定ロールだけに限定します。Botがメンションできない場合はメンションを省略します。\n"
                            + "メッセージの編集では再通知されません。"), ActionRow.of(sound.build()), ActionRow.of(updates.build()),
                    ActionRow.of(role.build()), ActionRow.of(Button.secondary(prefix + "preview", "表示サンプル（自分だけ）")));
        }
        var channels = Entities.create(prefix + "channels", Entities.Target.CHANNEL).setChannelTypes(Channel.Type.GUILD_TEXT)
                .setMinValues(0).setMaxValues(10).setPlaceholder("通知先チャンネル（最大10件）")
                .setDefaultValues(config.channels().stream().map(Entities::channel).toList());
        var kinds = Choices.create(prefix + "kinds").addOption("緊急地震速報（警報）", "EEW_WARNING").addOption("観測地震情報", "EARTHQUAKE")
                .addOption("津波警報・注意報・解除", "TSUNAMI").setMinValues(0).setMaxValues(3).setDefaultValues(config.kinds());
        return Ui.append(container, TextDisplay.of("サーバーごとに通知先と条件を保存します。\n"
                        + "初期状態は無効です。通知先を選んで有効にしてください。\n訓練・テスト報は通常配信せず、古い情報を新しい速報として送信しません。"),
                ActionRow.of(channels.build()), ActionRow.of(kinds.build()));
    }
    private static String summary(List<String> keywords) { return keywords.isEmpty() ? "全国" : Ui.escape(String.join("、", keywords)); }
}
