package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.settings.SettingsRepository;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class LogShareListener extends ListenerAdapter {
    public static final String COMMAND = "mclo.gsで共有";
    private final WorkExecutor worker;
    private final SettingsRepository settings;
    private final LogShareService service;
    private final LogShareDownloads downloads;
    public LogShareListener(SettingsRepository settings, LogShareRepository repository, WorkExecutor worker, String version) {
        this.settings = settings; this.worker = worker; service = new LogShareService(settings, repository, version);
        downloads = new LogShareDownloads(settings, worker, version);
    }
    @Override public void onButtonInteraction(ButtonInteractionEvent event) { downloads.handle(event); }
    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()
                || event.getMessage().getAttachments().stream().noneMatch(a -> LogDetector.supported(a.getFileName()))) return;
        worker.submit(() -> {
            long parent = event.getChannel() instanceof ThreadChannel thread ? thread.getParentChannel().getIdLong() : 0;
            if (!LogSharePolicy.automatic(settings.get(event.getGuild().getIdLong()), event.getChannel().getIdLong(), parent)) return;
            try {
                var member = event.getGuild().retrieveMemberById(event.getAuthor().getIdLong()).complete();
                service.share(event.getMessage(), member, true);
            } catch (Exception error) {
                LoggerFactory.getLogger(LogShareListener.class).warn("Automatic log share failed in guild {}: {}",
                        event.getGuild().getId(), error.getClass().getSimpleName());
                if (error instanceof LogShareService.Failure) event.getMessage()
                        .reply(Ui.text("LogShare", LogShareMessages.error(error))).mentionRepliedUser(false)
                        .queue(ignored -> {}, ignored -> {});
            }
        });
    }
    @Override public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!event.getName().equals(COMMAND) || !event.isFromGuild()) return;
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    var member = event.getGuild().retrieveMemberById(event.getUser().getIdLong()).complete();
                    var message = event.getTarget().getChannel().retrieveMessageById(event.getTarget().getIdLong()).complete();
                    var urls = service.share(message, member, false);
                    hook.sendMessage(Ui.text("LogShare", "元のメッセージへの返信から開けます。\n" + String.join("\n", urls))).queue();
                } catch (Exception error) { hook.sendMessage(Ui.text("LogShare", LogShareMessages.error(error))).queue(); }
            })) hook.sendMessage(Ui.text("LogShare", "現在混み合っています。")).queue();
        });
    }
    @Override public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || !event.getEmoji().getName().equals("📋")) return;
        worker.submit(() -> {
            if (!settings.get(event.getGuild().getIdLong()).enabled("logshare.enabled")) return;
            var user = event.retrieveUser().complete();
            if (user.isBot()) return;
            var message = event.retrieveMessage().complete();
            try {
                var member = event.getGuild().retrieveMemberById(user.getIdLong()).complete();
                service.share(message, member, false);
            } catch (Exception error) {
                LoggerFactory.getLogger(LogShareListener.class).warn("Reaction log share failed in guild {}: {}",
                        event.getGuild().getId(), error.getClass().getSimpleName());
                // Do not post errors for unauthorized reactions on somebody else's message.
                if (user.getIdLong() == message.getAuthor().getIdLong()) {
                    message.reply(Ui.text("LogShare", LogShareMessages.error(error))).mentionRepliedUser(false)
                            .queue(ignored -> {}, ignored -> {});
                }
            }
        });
    }
}
