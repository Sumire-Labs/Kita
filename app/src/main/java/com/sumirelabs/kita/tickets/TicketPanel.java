package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.MessageCreateSpec;

public final class TicketPanel {
    private TicketPanel() {}
    public static MessageCreateSpec reception(GuildSettings settings) {
        return Ui.message(List.of(Container.of(TextDisplay.of("## お問い合わせ\n"
                + settings.value("ticket.welcome", "ボタンからチケットを作成してください。担当者が対応します。")),
                ActionRow.of(Button.primary("ticket:open", "チケットを作成")))));
    }

    public static MessageCreateSpec controls(String subject, String details) {
        return Ui.message(List.of(Container.of(TextDisplay.of("## " + Ui.safe(subject) + "\n" + Ui.safe(details)),
                ActionRow.of(Button.primary("ticket:claim", "担当する"),
                        Button.danger("ticket:close", "クローズ")))));
    }
}
