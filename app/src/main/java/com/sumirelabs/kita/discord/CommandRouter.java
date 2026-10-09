package com.sumirelabs.kita.discord;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class CommandRouter extends ListenerAdapter {
    private final Map<String, Command> commands;
    private final WorkExecutor executor;

    public CommandRouter(Collection<Command> commands, WorkExecutor executor) {
        this.commands = commands.stream().collect(Collectors.toUnmodifiableMap(
                command -> command.definition().getName(), Function.identity()));
        this.executor = executor;
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        var command = commands.get(event.getName());
        if (command == null) return;
        if (!event.isFromGuild()) {
            event.reply(Ui.text("Kita", "サーバー内で使用してください。")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(command.privateReply()).queue(hook -> dispatch(command, CommandContext.slash(event)));
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        var content = event.getMessage().getContentRaw();
        if (!content.startsWith("k!")) return;
        var parts = content.substring(2).strip().split("\\s+", 2);
        var command = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) return;
        if (command.privateReply()) {
            event.getMessage().reply(Ui.text("Kita", "この操作は /" + parts[0] + " を使用してください。"))
                    .mentionRepliedUser(false).queue();
            return;
        }
        var arguments = parts.length == 2 ? Arrays.asList(parts[1].split("\\s+")) : java.util.List.<String>of();
        dispatch(command, CommandContext.prefix(event, arguments));
    }

    private void dispatch(Command command, CommandContext context) {
        if (!executor.submit(() -> {
            try { command.execute(context); }
            catch (IllegalArgumentException error) { context.reply("入力を確認してください", error.getMessage()); }
            catch (Exception error) {
                LoggerFactory.getLogger(CommandRouter.class).error("Command {} failed", command.definition().getName(), error);
                context.reply("Kita", "処理に失敗しました。時間をおいて再試行してください。");
            }
        })) context.reply("Kita", "現在混み合っています。少し待って再試行してください。");
    }
}
