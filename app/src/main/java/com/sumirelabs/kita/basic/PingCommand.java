package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public final class PingCommand implements Command {
    @Override public ApplicationCommandRequest definition() { return Commands.slash("ping", "Kitaの応答速度を表示"); }
    @Override public Mono<Void> execute(CommandContext context) {
        long start = System.nanoTime();
        return context.client().getRestClient().getUserService().getCurrentUser().then(Mono.defer(() -> {
            long rest = (System.nanoTime() - start) / 1_000_000;
            long gateway = context.client().getGatewayClient(context.shardId()).map(client -> client.getResponseTime().toMillis()).orElse(0L);
            return context.reply("Pong!", "Gateway: **" + gateway + " ms**\nREST: **" + rest + " ms**");
        }));
    }
}
