package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.discord.Ui;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class EarthquakeMessages {
    private EarthquakeMessages() {}
    public static MessageCreateSpec render(EarthquakeNotice notice, boolean silent, long role) {
        var lines = new ArrayList<String>();
        lines.add("## " + (notice.cancelled() ? "ℹ️ " : notice.kind() == EarthquakeNotice.Kind.EARTHQUAKE ? "🌏 " : "⚠️ ") + Ui.escape(notice.title()));
        if (role != 0) lines.add("<@&" + role + ">");
        lines.add("発表：<t:" + notice.issuedMs() / 1000 + ":F>");
        if (notice.kind() == EarthquakeNotice.Kind.EEW_WARNING || notice.kind() == EarthquakeNotice.Kind.EEW_FORECAST) lines.add("第" + notice.revision() + "報");
        if (notice.occurredMs() > 0) lines.add("発生：<t:" + notice.occurredMs() / 1000 + ":F>");
        if (!notice.place().isBlank()) lines.add("震央：**" + Ui.escape(notice.place()) + "**");
        if (notice.kind() == EarthquakeNotice.Kind.EARTHQUAKE) {
            lines.add("最大観測震度：**" + SeismicIntensity.label(notice.maxScale()) + "**"
                    + (notice.magnitude() == null ? "" : " · M" + notice.magnitude())
                    + (notice.depth() == null ? "" : " · 深さ " + (notice.depth() == 0 ? "ごく浅い" : notice.depth() + "km")));
        }
        if (!notice.areas().isEmpty()) {
            lines.add(notice.kind() == EarthquakeNotice.Kind.EEW_WARNING ? "### 警報対象地域" : "### 地域別情報");
            var areas = notice.areas().stream().map(area -> Ui.escape(area.prefecture().equals(area.name()) ? area.name()
                            : area.prefecture() + " " + area.name())
                    + (notice.kind() == EarthquakeNotice.Kind.EARTHQUAKE ? "：震度" + SeismicIntensity.label(area.scale()) : "")
                    + (area.detail().isBlank() ? "" : " · " + Ui.escape(area.detail()))).distinct().toList();
            lines.addAll(areas.stream().limit(18).toList());
            if (areas.size() > 18) lines.add("-# ほか " + (areas.size() - 18) + " 地域");
        }
        String notes = notice.notes().stream().map(Ui::escape).collect(Collectors.joining("\n"));
        if (!notes.isBlank()) lines.add(notes.substring(0, Math.min(1300, notes.length())));
        lines.add("-# 気象庁の発表を" + Ui.escape(notice.source()) + "経由で表示。情報の到達には遅延・欠落があります。");
        String text = String.join("\n", lines);
        if (text.length() > 3800) text = text.substring(0, 3750) + "\n…詳細は情報元をご確認ください。";
        var message = Ui.message(List.of(Container.of(TextDisplay.of(text), ActionRow.of(
                Button.link(notice.sourceUrl(), "情報元を開く")))));
        var mentions = AllowedMentions.builder().parseType().repliedUser(false);
        if (role != 0) mentions.allowRole(discord4j.common.util.Snowflake.of(role));
        return message.withAllowedMentions(mentions.build()).withFlags(silent
                ? List.of(Message.Flag.IS_COMPONENTS_V2, Message.Flag.SUPPRESS_NOTIFICATIONS) : List.of(Message.Flag.IS_COMPONENTS_V2));
    }
    public static MessageCreateSpec preview() {
        return Ui.text("【通知テスト】地震・津波通知", "これは表示確認用のサンプルです。実際の地震情報ではありません。\n"
                + "緊急地震速報（警報）・観測地震情報・津波情報をこの形式のComponents V2で表示します。");
    }
}
