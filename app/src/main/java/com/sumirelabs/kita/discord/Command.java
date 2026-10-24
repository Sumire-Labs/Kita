package com.sumirelabs.kita.discord;

import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public interface Command {
    ApplicationCommandRequest definition();
    Mono<Void> execute(CommandContext context);
    default boolean privateReply() { return false; }
}
