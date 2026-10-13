package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.Ui;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class EventLogMessages {
    private EventLogMessages() {}
    public static MessageCreateSpec render(LogEntry entry, EventLogConfig config) {
        var body = new StringBuilder("## EventLog · ").append(entry.action().label()).append("\n");
        if (entry.subject() != 0) body.append("対象：<@").append(entry.subject()).append(">\n");
        if (entry.channel() != 0) body.append("チャンネル：<#").append(entry.channel()).append(">\n");
        body.append("実行者：").append(entry.actor() == 0 ? "不明／このイベントでは特定不可" : "<@" + entry.actor() + ">").append("\n");
        body.append("日時：<t:").append(entry.time() / 1000).append(":F>\n");
        for (var field : entry.fields()) {
            if (!config.bodies() && entry.action().category() == LogAction.Category.MESSAGES && SetBody.labels(field.label())) continue;
            body.append("### ").append(Ui.escape(field.label())).append("\n").append(Ui.escape(field.value())).append("\n");
        }
        String text = MessageSnapshot.limit(body.toString(), 3750);
        var container = Container.of(TextDisplay.of(text));
        if (!entry.link().isBlank()) container = Ui.append(container, ActionRow.of(Button.link(entry.link(), "メッセージへ移動")));
        var result = Ui.message(List.of(container));
        if (!config.silent()) result = result.withFlags(List.of(Message.Flag.IS_COMPONENTS_V2));
        if (config.bodies() && !entry.fileText().isBlank()) result = Ui.file(result, entry.fileName(), entry.fileText().getBytes(StandardCharsets.UTF_8));
        return result;
    }
    private static final class SetBody {
        static boolean labels(String label) { return java.util.Set.of("変更前", "変更後", "本文", "添付ファイル").contains(label); }
    }
}
