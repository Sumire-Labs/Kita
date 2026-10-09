package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public final class TicketCommand implements Command {
    private final SettingsRepository settings;
    public TicketCommand(SettingsRepository settings) { this.settings = settings; }
    @Override public boolean privateReply() { return true; }
    @Override public ApplicationCommandRequest definition() { return Commands.slash("ticket", "チケットの受付パネルを表示"); }
    @Override public Mono<Void> execute(CommandContext context) {
        return settings.get(context.guildId()).flatMap(config -> config.enabled("ticket.enabled")
                ? context.reply(TicketPanel.reception(config)) : context.reply("Ticket", "チケットは無効です。"));
    }
}
