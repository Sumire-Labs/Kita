package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Replies;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import reactor.core.publisher.Mono;

public final class LogShareListener {
    public static final String COMMAND = "mclo.gsで共有";
    private final SettingsRepository settings;
    private final LogShareService service;
    private final LogShareDownloads downloads;
    public LogShareListener(SettingsRepository settings, LogShareRepository repository, String version) {
        this.settings = settings; service = new LogShareService(settings, repository, version); downloads = new LogShareDownloads(settings, version);
    }
    public Mono<Void> button(ButtonInteractionEvent event) { return downloads.handle(event); }
    public Mono<Void> message(MessageCreateEvent event) {
        var message = event.getMessage();
        if (message.getGuildId().isEmpty() || message.getAuthor().map(user -> user.isBot()).orElse(true)
                || message.getWebhookId().isPresent() || message.getAttachments().stream().noneMatch(a -> LogDetector.supported(a.getFilename()))) return Mono.empty();
        return settings.get(message.getGuildId().orElseThrow().asLong()).flatMap(config -> message.getChannel().ofType(GuildMessageChannel.class)
                .filter(channel -> LogSharePolicy.automatic(config, channel.getId().asLong(), LogSharePolicy.parent(channel)))
                .flatMap(channel -> service.share(message, message.getAuthor().orElseThrow().getId().asLong(), true)
                        .onErrorResume(error -> Replies.reference(message, Ui.text("LogShare", LogShareMessages.error(error))).then(Mono.empty())))).then();
    }
    public Mono<Void> context(MessageInteractionEvent event) {
        if (!event.getCommandName().equals(COMMAND) || event.getInteraction().getGuildId().isEmpty()) return Mono.empty();
        var context = new InteractionContext(event);
        return context.defer(true).then(event.getTargetMessage().flatMap(message -> service.share(message, context.userId(), false)))
                .flatMap(urls -> context.followup(Ui.text("LogShare", "元のメッセージへの返信から開けます。\n" + String.join("\n", urls)), true))
                .onErrorResume(error -> context.followup(Ui.text("LogShare", LogShareMessages.error(error)), true));
    }
    public Mono<Void> reaction(ReactionAddEvent event) {
        if (event.getGuildId().isEmpty() || !event.getEmoji().asUnicodeEmoji().map(emoji -> emoji.getRaw().equals("📋")).orElse(false)) return Mono.empty();
        return settings.get(event.getGuildId().orElseThrow().asLong()).filter(config -> config.enabled("logshare.enabled"))
                .flatMap(config -> event.getUser().filter(user -> !user.isBot()).flatMap(user -> event.getMessage().flatMap(message ->
                        service.share(message, user.getId().asLong(), false).onErrorResume(error ->
                                message.getAuthor().map(author -> author.getId().equals(user.getId())).orElse(false)
                                        ? Replies.reference(message, Ui.text("LogShare", LogShareMessages.error(error))).then(Mono.empty()) : Mono.empty())))).then();
    }
}
