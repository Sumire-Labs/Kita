package com.sumirelabs.kita.discord;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

public final class CommandRouter {
    private final Map<String, Command> commands;
    public CommandRouter(Collection<Command> commands) {
        this.commands = commands.stream().collect(Collectors.toUnmodifiableMap(command -> command.definition().name(), Function.identity()));
    }
    public Mono<Void> slash(ChatInputInteractionEvent event) {
        var command = commands.get(event.getCommandName()); if (command == null) return Mono.empty();
        if (event.getInteraction().getGuildId().isEmpty()) return Replies.privateReply(event, Ui.text("Kita", "サーバー内で使用してください。"));
        var context = new CommandContext(event.getClient(), event.getInteraction().getGuildId().get().asLong(),
                event.getInteraction().getChannelId().asLong(), event.getInteraction().getUser(), List.of(), event, null, command.privateReply(), event.getShardInfo().getIndex());
        return event.deferReply().withEphemeral(command.privateReply()).then(dispatch(command, context));
    }
    public Mono<Void> prefix(MessageCreateEvent event) {
        var message = event.getMessage();
        if (message.getGuildId().isEmpty() || message.getAuthor().map(user -> user.isBot()).orElse(true) || message.getWebhookId().isPresent()) return Mono.empty();
        String content = message.getContent(); if (!content.startsWith("k!")) return Mono.empty();
        var parts = content.substring(2).strip().split("\\s+", 2);
        var command = commands.get(parts[0].toLowerCase(Locale.ROOT)); if (command == null) return Mono.empty();
        if (command.privateReply()) return Replies.reference(message, Ui.text("Kita", "この操作は /" + parts[0] + " を使用してください。")).then();
        var arguments = parts.length == 2 ? Arrays.asList(parts[1].split("\\s+")) : List.<String>of();
        return dispatch(command, new CommandContext(event.getClient(), message.getGuildId().get().asLong(), message.getChannelId().asLong(),
                message.getAuthor().orElseThrow(), arguments, null, message, false, event.getShardInfo().getIndex()));
    }
    private Mono<Void> dispatch(Command command, CommandContext context) {
        return Mono.defer(() -> command.execute(context)).onErrorResume(IllegalArgumentException.class,
                error -> context.reply("入力を確認してください", error.getMessage())).onErrorResume(error -> {
                    org.slf4j.LoggerFactory.getLogger(CommandRouter.class).warn("Command {} failed: {}", command.definition().name(), error.getClass().getSimpleName());
                    return context.reply("Kita", "処理に失敗しました。時間をおいて再試行してください。");
                });
    }
}
