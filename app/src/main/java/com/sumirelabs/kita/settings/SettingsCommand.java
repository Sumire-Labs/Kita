package com.sumirelabs.kita.settings;

import com.sumirelabs.kita.discord.*;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

public final class SettingsCommand implements Command {
    private final SettingsRepository repository;
    public SettingsCommand(SettingsRepository repository) { this.repository = repository; }
    @Override public boolean privateReply() { return true; }
    @Override public ApplicationCommandRequest definition() {
        return ApplicationCommandRequest.builder().from(Commands.slash("settings", "サーバーのKita設定パネルを開く"))
                .defaultMemberPermissions(Long.toString(Permission.MANAGE_GUILD.getValue())).build();
    }
    @Override public Mono<Void> execute(CommandContext context) {
        return context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD))
                .then(repository.get(context.guildId())).flatMap(settings -> context.reply(SettingsPanel.render(settings, context.user().getId().asLong(), "home")));
    }
}
