package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.spec.MessageCreateSpec;

final class LogShareMessages {
    private LogShareMessages() {}
    static MessageCreateSpec shared(String name, String url) {
        String label = Ui.safe(Ui.escape(name.substring(0, Math.min(name.length(), 160))));
        return Ui.message(List.of(Container.of(TextDisplay.of("## LogShare\nファイル名: " + label),
                ActionRow.of(Button.link(url, Emoji.unicode("📄"), "mclo.gsでログを開く"),
                        Button.secondary(LogShareDownloads.buttonId(name, url), Emoji.unicode("⬇️"), "ログをダウンロード")))));
    }
    static String error(Throwable error) {
        return error(error, "ログの共有に失敗しました。添付ファイル・Botの権限・接続を確認して再試行してください。");
    }
    static String error(Throwable error, String fallback) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException) return cause.getMessage();
        }
        return fallback;
    }
}
