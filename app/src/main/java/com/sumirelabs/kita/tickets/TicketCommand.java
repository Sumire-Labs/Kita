package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class TicketCommand implements Command {
    private final SettingsRepository settings;
    public TicketCommand(SettingsRepository settings) { this.settings = settings; }
    @Override public boolean privateReply() { return true; }
    @Override public CommandData definition() { return Commands.slash("ticket", "チケットの受付パネルを表示"); }
    @Override public void execute(CommandContext context) {
        var config = settings.get(context.guild().getIdLong());
        if (!config.enabled("ticket.enabled")) { context.reply("Ticket", "チケットは無効です。"); return; }
        context.reply(TicketPanel.reception(config));
    }
}
