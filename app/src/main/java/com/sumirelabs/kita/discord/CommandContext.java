package com.sumirelabs.kita.discord;

import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public record CommandContext(JDA jda, Guild guild, Member member, User user,
                             GuildMessageChannel channel, List<String> arguments,
                             SlashCommandInteractionEvent slash, Consumer<MessageCreateData> responder) {
    public static CommandContext slash(SlashCommandInteractionEvent event) {
        return new CommandContext(event.getJDA(), event.getGuild(), event.getMember(), event.getUser(),
                event.getGuildChannel(), List.of(), event,
                data -> event.getHook().sendMessage(data).queue());
    }

    public static CommandContext prefix(MessageReceivedEvent event, List<String> arguments) {
        return new CommandContext(event.getJDA(), event.getGuild(), event.getMember(), event.getAuthor(),
                event.getGuildChannel(), arguments, null,
                data -> event.getMessage().reply(data).mentionRepliedUser(false).queue());
    }

    public String option(String name, String fallback) {
        if (slash != null) {
            var value = slash.getOption(name);
            return value == null ? fallback : value.getAsString();
        }
        return arguments.isEmpty() ? fallback : String.join(" ", arguments);
    }

    public void reply(String title, String body) { reply(Ui.text(title, body)); }
    public void reply(MessageCreateData data) { responder.accept(data); }
}
