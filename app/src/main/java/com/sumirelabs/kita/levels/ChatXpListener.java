package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Access;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.ThreadChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

public final class ChatXpListener {
    private final LevelsService levels;
    private final ChatXpGate gate = new ChatXpGate();
    public ChatXpListener(LevelsService levels) { this.levels = levels; }
    public Mono<Void> message(MessageCreateEvent event) {
        var message = event.getMessage();
        if (message.getGuildId().isEmpty() || message.getAuthor().map(user -> user.isBot()).orElse(true) || message.getWebhookId().isPresent()) return Mono.empty();
        String text = message.getContent().strip();
        if (text.isBlank() || text.startsWith("k!") || text.startsWith("/")) return Mono.empty();
        long guild = message.getGuildId().orElseThrow().asLong(), user = message.getAuthor().orElseThrow().getId().asLong();
        var now = message.getTimestamp();
        if (gate.blocked(guild, user, now, null)) return Mono.empty();
        return levels.settings(guild).map(LevelConfig::from).filter(config -> config.enabled() && config.chat())
                .flatMap(config -> message.getChannel().filter(channel -> config.channel(channel.getId().asLong(),
                                channel instanceof ThreadChannel thread ? thread.getParentId().map(id -> id.asLong()).orElse(0L) : 0))
                        .flatMap(channel -> event.getMember().map(Mono::just).orElseGet(() -> Access.member(event.getClient(), guild, user)))
                        .filter(member -> config.member(member.getRoleIds().stream().map(id -> id.asString()).collect(Collectors.toSet())))
                        .flatMap(member -> {
                            byte[] hash;
                            try { hash = MessageDigest.getInstance("SHA-256").digest(text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)); }
                            catch (java.security.NoSuchAlgorithmException error) { return Mono.error(error); }
                            if (gate.blocked(guild, user, now, hash)) return Mono.empty();
                            return member.getGuild().flatMap(server -> levels.award(server, user, LevelsRepository.Source.CHAT, config.chatXp(), now, hash, 0,
                                    message.getChannelId().asLong())).doOnNext(result -> {
                                if (result.accepted()) gate.accepted(guild, user, now, config.cooldown(), hash);
                            });
                        })).then();
    }
}
