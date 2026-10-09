package com.sumirelabs.kita.discord;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface Command {
    CommandData definition();
    void execute(CommandContext context) throws Exception;
    default boolean privateReply() { return false; }
}
