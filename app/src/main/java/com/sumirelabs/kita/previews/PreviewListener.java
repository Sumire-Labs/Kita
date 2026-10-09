package com.sumirelabs.kita.previews;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.time.Duration;
import java.util.Map;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class PreviewListener extends ListenerAdapter {
    private final SettingsRepository settings;
    private final WorkExecutor worker;
    private final Map<String, PreviewProvider> providers;
    private final Cache<String, Preview> cache = Caffeine.newBuilder().maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(5)).build();

    public PreviewListener(SettingsRepository settings, WorkExecutor worker, Map<String, PreviewProvider> providers) {
        this.settings = settings; this.worker = worker; this.providers = Map.copyOf(providers);
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        var links = SocialLink.find(event.getMessage().getContentRaw());
        if (links.isEmpty()) return;
        worker.submit(() -> {
            var config = settings.get(event.getGuild().getIdLong());
            if (!config.enabled("previews.enabled")) return;
            boolean sent = false;
            for (var link : links) {
                if (!config.enabled("previews." + link.platform())) continue;
                var provider = providers.get(link.platform());
                if (provider == null) continue;
                try {
                    var preview = cache.getIfPresent(link.uri().toString());
                    if (preview == null) {
                        preview = provider.fetch(link);
                        cache.put(link.uri().toString(), preview);
                    }
                    boolean nsfw = event.getChannel() instanceof TextChannel text && text.isNSFW();
                    event.getMessage().reply(PreviewRenderer.render(preview, nsfw)).mentionRepliedUser(false).complete();
                    sent = true;
                } catch (Exception error) {
                    LoggerFactory.getLogger(PreviewListener.class).warn("{} preview failed: {}", link.platform(), error.getClass().getSimpleName());
                }
            }
            if (sent && event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE)) {
                event.getMessage().suppressEmbeds(true).queue();
            }
        });
    }
}
