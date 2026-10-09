package com.sumirelabs.kita.app;

import com.sumirelabs.kita.discord.Command;
import java.util.List;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class CommandRegistration extends ListenerAdapter {
    private final List<Command> commands;
    private final long developmentGuildId;
    public CommandRegistration(List<Command> commands, long developmentGuildId) {
        this.commands = List.copyOf(commands); this.developmentGuildId = developmentGuildId;
    }
    @Override public void onReady(ReadyEvent event) {
        var definitions = commands.stream().map(Command::definition).toList();
        var logger = LoggerFactory.getLogger(CommandRegistration.class);
        if (developmentGuildId == 0 && event.getJDA().getShardInfo().getShardId() == 0) {
            event.getJDA().updateCommands().addCommands(definitions)
                    .queue(ignored -> logger.info("Registered global commands"), error -> logger.error("Command registration failed", error));
        } else if (developmentGuildId != 0) {
            var guild = event.getJDA().getGuildById(developmentGuildId);
            if (guild != null) guild.updateCommands().addCommands(definitions)
                    .queue(ignored -> logger.info("Registered development guild commands"), error -> logger.error("Command registration failed", error));
        }
    }
}
