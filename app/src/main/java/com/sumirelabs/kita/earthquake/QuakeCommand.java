package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public final class QuakeCommand implements Command {
    private final EarthquakeProvider provider;
    public QuakeCommand(EarthquakeProvider provider) { this.provider = provider; }
    @Override public ApplicationCommandRequest definition() { return Commands.slash("quake", "直近の観測地震情報を表示"); }
    @Override public Mono<Void> execute(CommandContext context) {
        return provider.latest().flatMap(notice -> context.reply(EarthquakeMessages.render(notice, true, 0)));
    }
}