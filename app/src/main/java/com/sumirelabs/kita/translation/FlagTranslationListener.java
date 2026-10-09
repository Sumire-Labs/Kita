package com.sumirelabs.kita.translation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.time.Duration;
import java.util.Map;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class FlagTranslationListener extends ListenerAdapter {
    private final SettingsRepository settings;
    private final TranslationService translator;
    private final WorkExecutor worker;
    private final Map<String, String> languages;
    private final Cache<String, Boolean> completed = Caffeine.newBuilder().maximumSize(20_000)
            .expireAfterWrite(Duration.ofHours(1)).build();
    private final Cache<Long, Boolean> cooldown = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(5)).build();

    public FlagTranslationListener(SettingsRepository settings, TranslationService translator,
                                   WorkExecutor worker, Map<String, String> languages) {
        this.settings = settings; this.translator = translator; this.worker = worker;
        this.languages = Map.copyOf(languages);
    }

    @Override public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || !translator.available()) return;
        var target = FlagLanguages.target(event.getEmoji().getName(), languages);
        if (target.isEmpty()) return;
        worker.submit(() -> {
            if (!settings.get(event.getGuild().getIdLong()).enabled("translation.enabled")) return;
            var user = event.retrieveUser().complete();
            if (user.isBot() || cooldown.asMap().putIfAbsent(user.getIdLong(), true) != null) return;
            var message = event.retrieveMessage().complete();
            var source = message.getContentRaw();
            if (message.getAuthor().isBot() || source.isBlank()) return;
            var key = event.getGuild().getId() + ":" + message.getId() + ":" + target.get() + ":" + message.getTimeEdited();
            if (completed.asMap().putIfAbsent(key, true) != null) return;
            try {
                var translated = translator.translate(source, target.get());
                message.reply(Ui.longText(event.getEmoji().getName() + " " + target.get(), translated))
                        .mentionRepliedUser(false).complete();
            } catch (Exception error) {
                completed.invalidate(key);
                LoggerFactory.getLogger(FlagTranslationListener.class).warn("Flag translation failed: {}", error.getClass().getSimpleName());
            }
        });
    }
}
