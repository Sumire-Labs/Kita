package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.ui.Entities;
import com.sumirelabs.kita.discord.ui.Choices;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.channel.Channel;

final class LevelRewardSettings {
    private LevelRewardSettings() {}
    static Container append(Container base, GuildSettings settings, String prefix) {
        var config = LevelConfig.from(settings); var rewards = LevelReward.read(settings);
        String rows = rewards.stream().sorted(java.util.Comparator.comparingInt(LevelReward::level))
                .map(reward -> "Lv." + reward.level() + " → <@&" + reward.roleId() + ">")
                .collect(java.util.stream.Collectors.joining("\n"));
        var mode = Choices.create(prefix + "rewardMode").addOption("報酬を累積", "stack")
                .addOption("到達済みの最高レベルのみ", "highest").setDefaultValues(config.stackRewards() ? "stack" : "highest");
        var notice = Choices.create(prefix + "notice").addOption("通知しない", "off")
                .addOption("報酬獲得時だけ通知", "rewards").addOption("レベルアップ時に通知", "levels").setDefaultValues(config.notice());
        var channel = Entities.create(prefix + "noticeChannel", Entities.Target.CHANNEL)
                .setChannelTypes(Channel.Type.GUILD_TEXT).setMinValues(0).setMaxValues(1).setPlaceholder("通知先（未指定: Chatの投稿先・VCは通知なし）");
        if (config.noticeChannel() != 0) channel.setDefaultValues(Entities.channel(config.noticeChannel()));
        var container = Ui.append(base, TextDisplay.of("### ロール報酬\n" + (rows.isEmpty() ? "報酬は未設定です。" : rows)
                        + "\n-# 報酬の編集にはサーバー管理・ロール管理権限が必要です。変更は次のXP獲得／プロフィール表示時に反映します。\n"
                        + "-# 最高レベル方式でも、手動で付けたロールは削除しません。通知はサイレントです。"),
                ActionRow.of(mode.build()), ActionRow.of(Entities.create(prefix + "rewardRole", Entities.Target.ROLE)
                        .setPlaceholder("報酬ロールを選んで必要レベルを設定（25件まで）").build()));
        if (!rewards.isEmpty()) {
            var delete = Choices.create(prefix + "rewardDelete").setPlaceholder("報酬設定を削除");
            rewards.forEach(reward -> delete.addOption("Lv." + reward.level() + " · ロール " + reward.roleId(), String.valueOf(reward.roleId())));
            container = Ui.append(container, ActionRow.of(delete.build()));
        }
        return Ui.append(container, ActionRow.of(notice.build()), ActionRow.of(channel.build()));
    }
}
