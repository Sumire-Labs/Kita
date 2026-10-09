package com.sumirelabs.kita.translation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.ReactiveWork;
import com.sumirelabs.kita.discord.Replies;
import com.sumirelabs.kita.discord.Ui;
import discord4j.core.event.domain.message.ReactionAddEvent;
import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class FlagTranslationListener {
    private final SettingsRepository settings;
    private final TranslationService translator;
    private final Map<String, String> languages;
    private final Cache<String, Boolean> completed = Caffeine.newBuilder().maximumSize(20_000)
            .expireAfterWrite(Duration.ofHours(1)).build();
    private final Cache<Long, Boolean> cooldown = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(5)).build();

    public FlagTranslationListener(SettingsRepository settings, TranslationService translator, Map<String, String> languages) {
        this.settings = settings; this.translator = translator; this.languages = Map.copyOf(languages);
    }

    public Mono<Void> reaction(ReactionAddEvent event) {
        if (event.getGuildId().isEmpty() || !translator.available()) return Mono.empty();
        var emoji = event.getEmoji().asUnicodeEmoji().map(e -> e.getRaw()).orElse("");
        var target = FlagLanguages.target(emoji, languages);
        if (target.isEmpty()) return Mono.empty();
        return settings.get(event.getGuildId().orElseThrow().asLong()).flatMap(config -> {
            if (!config.enabled("translation.enabled")) return Mono.empty();
            return event.getUser().filter(user -> !user.isBot())
                    .filter(user -> cooldown.asMap().putIfAbsent(user.getId().asLong(), true) == null)
                    .flatMap(user -> event.getMessage()).filter(message -> !message.getContent().isBlank()
                            && !message.getAuthor().map(u -> u.isBot()).orElse(true))
                    .flatMap(message -> Mono.defer(() -> {
                        var key = event.getGuildId().orElseThrow().asString() + ":" + message.getId().asString()
                                + ":" + target.orElseThrow() + ":" + message.getEditedTimestamp();
                        if (completed.asMap().putIfAbsent(key, true) != null) return Mono.empty();
                        return ReactiveWork.io(() -> translator.translate(message.getContent(), target.orElseThrow()))
                                .flatMap(text -> Replies.reference(message, Ui.longText(emoji + " " + target.orElseThrow(), text)))
                                .doOnError(error -> completed.invalidate(key)).then();
                    }));
        });
    }
}
