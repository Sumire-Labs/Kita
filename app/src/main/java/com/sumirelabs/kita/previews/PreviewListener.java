package com.sumirelabs.kita.previews;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Replies;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageEditSpec;
import discord4j.rest.util.Permission;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class PreviewListener {
    private final SettingsRepository settings;
    private final PreviewReplacement replacement;
    public PreviewListener(SettingsRepository settings, Map<String, PreviewProvider> providers) {
        this.settings = settings; replacement = new PreviewReplacement(providers);
    }
    public Mono<Void> message(MessageCreateEvent event) {
        var message = event.getMessage();
        if (message.getGuildId().isEmpty() || message.getAuthor().map(user -> user.isBot()).orElse(true) || message.getWebhookId().isPresent()) return Mono.empty();
        var links = SocialLink.find(message.getContent());
        if (links.isEmpty()) return Mono.empty();
        return settings.get(message.getGuildId().orElseThrow().asLong()).flatMap(config -> {
            if (!config.enabled("previews.enabled")) return Mono.empty();
            var active = links.stream().filter(link -> config.enabled("previews." + link.platform())).toList();
            boolean maySuppress = !active.isEmpty() && active.size() == links.size() && SocialLink.urlCount(message.getContent()) == links.size();
            return message.getChannel().flatMap(channel -> replacement.replace(active, maySuppress,
                    preview -> Replies.reference(message, PreviewRenderer.render(preview, channel instanceof TextChannel text && text.isNsfw())).then(),
                    (link, error) -> {
                        LoggerFactory.getLogger(getClass()).warn("{} preview failed: {}", link.platform(), error.getClass().getSimpleName());
                        return Replies.reference(message, Ui.text("プレビューを取得できませんでした",
                                link.platform() + " の投稿を取得できませんでした。元のリンクから開いてください。")).then();
                    }, () -> {
                        if (!(channel instanceof GuildChannel guildChannel)) return Mono.empty();
                        return message.getGuild().flatMap(guild -> guild.getSelfMember())
                                .flatMap(member -> Access.channel(member, guildChannel, Permission.MANAGE_MESSAGES))
                                .then(message.edit(MessageEditSpec.builder().flags(List.of(Message.Flag.SUPPRESS_EMBEDS)).build())).then();
                    }));
        }).then();
    }
}
