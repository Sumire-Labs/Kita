package com.sumirelabs.kita.previews;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.util.Map;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class PreviewListener extends ListenerAdapter {
    private final SettingsRepository settings;
    private final WorkExecutor worker;
    private final PreviewReplacement replacement;

    public PreviewListener(SettingsRepository settings, WorkExecutor worker, Map<String, PreviewProvider> providers) {
        this.settings = settings; this.worker = worker; this.replacement = new PreviewReplacement(providers);
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        var links = SocialLink.find(event.getMessage().getContentRaw());
        if (links.isEmpty()) return;
        worker.submit(() -> {
            var config = settings.get(event.getGuild().getIdLong());
            if (!config.enabled("previews.enabled")) return;
            var active = links.stream().filter(link -> config.enabled("previews." + link.platform())).toList();
            boolean maySuppress = active.size() == links.size()
                    && SocialLink.urlCount(event.getMessage().getContentRaw()) == links.size()
                    && event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE);
            boolean nsfw = event.getChannel() instanceof TextChannel text && text.isNSFW();
            replacement.replace(active, maySuppress,
                    preview -> event.getMessage().reply(PreviewRenderer.render(preview, nsfw)).mentionRepliedUser(false).complete(),
                    (link, error) -> {
                        LoggerFactory.getLogger(PreviewListener.class).warn("{} preview failed in channel {} message {}: {}",
                                link.platform(), event.getChannel().getId(), event.getMessageId(), diagnostic(error));
                        event.getMessage().reply(Ui.text("プレビューを取得できませんでした",
                                link.platform() + " の投稿を取得できませんでした。元のリンクから開いてください。"))
                                .mentionRepliedUser(false).queue(ignored -> {}, sendError ->
                                        LoggerFactory.getLogger(PreviewListener.class).warn("Preview failure notice could not be sent: {}", diagnostic(sendError)));
                    }, () -> event.getMessage().suppressEmbeds(true).queue(ignored -> {}, error ->
                            LoggerFactory.getLogger(PreviewListener.class).warn("Source preview suppression failed: {}", diagnostic(error))));
        });
    }

    private static String diagnostic(Throwable error) {
        String detail = error instanceof IllegalStateException ? String.valueOf(error.getMessage()) : error.getClass().getSimpleName();
        detail = detail.replaceAll("https?://\\S+", "[URL]").replaceAll("[\\r\\n]+", " ");
        return detail.substring(0, Math.min(500, detail.length()));
    }
}
