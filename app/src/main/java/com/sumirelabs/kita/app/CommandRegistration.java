package com.sumirelabs.kita.app;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.logshare.LogShareListener;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

public final class CommandRegistration {
    private CommandRegistration() {}
    public static Mono<Void> register(GatewayDiscordClient client, List<Command> commands, long developmentGuild) {
        var definitions = new ArrayList<>(commands.stream().map(Command::definition).toList());
        definitions.add(ApplicationCommandRequest.builder().name(LogShareListener.COMMAND).type(3).contexts(List.of(0)).build());
        var service = client.getRestClient().getApplicationService();
        return client.getRestClient().getApplicationId().flatMapMany(id -> developmentGuild == 0
                ? service.bulkOverwriteGlobalApplicationCommand(id, definitions)
                : service.bulkOverwriteGuildApplicationCommand(id, developmentGuild, definitions)).then()
                .doOnSuccess(ignored -> org.slf4j.LoggerFactory.getLogger(CommandRegistration.class).info("Registered {} commands", definitions.size()));
    }
}
