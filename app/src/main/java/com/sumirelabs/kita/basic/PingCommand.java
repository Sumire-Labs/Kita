package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class PingCommand implements Command {
    @Override public CommandData definition() { return Commands.slash("ping", "Kitaの応答速度を表示"); }

    @Override public void execute(CommandContext context) {
        long rest = context.jda().getRestPing().complete();
        context.reply("Pong!", "Gateway: **" + context.jda().getGatewayPing() + " ms**\nREST: **" + rest + " ms**");
    }
}
