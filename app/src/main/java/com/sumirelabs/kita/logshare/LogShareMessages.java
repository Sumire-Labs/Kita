package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

final class LogShareMessages {
    private LogShareMessages() {}
    static MessageCreateData shared(String name, String url) {
        String label = Ui.safe(MarkdownSanitizer.escape(name.substring(0, Math.min(name.length(), 160))));
        return Ui.message(List.of(Container.of(TextDisplay.of("## LogShare\nファイル名: " + label),
                ActionRow.of(Button.link(url, "mclo.gsでログを開く").withEmoji(Emoji.fromUnicode("📄")),
                        Button.secondary(LogShareDownloads.buttonId(name, url), "ログをダウンロード")
                                .withEmoji(Emoji.fromUnicode("⬇️"))))));
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
