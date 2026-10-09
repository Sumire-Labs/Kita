package com.sumirelabs.kita.discord;

import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.core.object.command.ApplicationCommandOption;
import java.util.List;

public final class Commands {
    private Commands() {}
    public static ApplicationCommandRequest slash(String name, String description, ApplicationCommandOptionData... options) {
        return ApplicationCommandRequest.builder().name(name).description(description).type(1).contexts(List.of(0)).options(List.of(options)).build();
    }
    public static ApplicationCommandOptionData option(String name, String description, ApplicationCommandOption.Type type, boolean required) {
        return ApplicationCommandOptionData.builder().name(name).description(description).type(type.getValue()).required(required).build();
    }
}
