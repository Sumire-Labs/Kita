package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class TicketPanel {
    private TicketPanel() {}
    public static MessageCreateData reception(GuildSettings settings) {
        return Ui.message(List.of(Container.of(TextDisplay.of("## お問い合わせ\n"
                + settings.value("ticket.welcome", "ボタンからチケットを作成してください。担当者が対応します。")),
                ActionRow.of(Button.primary("ticket:open", "チケットを作成")))));
    }

    public static MessageCreateData controls(String subject, String details) {
        return Ui.message(List.of(Container.of(TextDisplay.of("## " + Ui.safe(subject) + "\n" + Ui.safe(details)),
                ActionRow.of(Button.primary("ticket:claim", "担当する"),
                        Button.danger("ticket:close", "クローズ")))));
    }
}
