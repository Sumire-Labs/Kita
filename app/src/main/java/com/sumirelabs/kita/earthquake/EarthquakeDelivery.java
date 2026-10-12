package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class EarthquakeDelivery {
    private final GatewayDiscordClient client;
    private final SettingsRepository settings;
    private final EarthquakeRepository repository;
    EarthquakeDelivery(GatewayDiscordClient client, SettingsRepository settings, EarthquakeRepository repository) {
        this.client = client; this.settings = settings; this.repository = repository;
    }
    Mono<Boolean> send(EarthquakeNotice notice) {
        return send(notice, () -> true);
    }
    Mono<Boolean> send(EarthquakeNotice notice, java.util.function.BooleanSupplier current) {
        return client.getGuilds().flatMap(guild -> settings.get(guild.getId().asLong()).flatMap(setting -> {
            if (!current.getAsBoolean()) return Mono.just(true);
            var config = EarthquakeConfig.from(setting);
            if (!config.enabled() || !config.kinds().contains(notice.kind().name())) return Mono.just(true);
            return Flux.fromIterable(config.channels()).concatMap(channel ->
                    deliver(notice, guild.getId().asLong(), Long.parseUnsignedLong(channel), config, current))
                    .reduce(true, (all, sent) -> all && sent);
        }).onErrorReturn(false), 8).reduce(true, (all, sent) -> all && sent).onErrorReturn(false);
    }
    private Mono<Boolean> deliver(EarthquakeNotice notice, long guild, long channel, EarthquakeConfig config, java.util.function.BooleanSupplier current) {
        return repository.delivery(notice.key(), guild, channel).defaultIfEmpty(new EarthquakeRepository.Delivery(0, "", ""))
                .flatMap(previous -> {
                    if (!current.getAsBoolean()) return Mono.empty();
                    if (previous.token().equals(notice.token()) || previous.messageId() == 0 && !config.matches(notice)) return Mono.empty();
                    if (previous.messageId() == 0 && notice.cancelled()) return Mono.empty();
                    return Mono.usingWhen(repository.claim(notice, guild, channel), lease -> client.getChannelById(Access.id(channel))
                                    .ofType(TextChannel.class).filter(target -> target.getGuildId().asLong() == guild)
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Notification channel unavailable")))
                                    .flatMap(target -> settings.get(guild).map(EarthquakeConfig::from).flatMap(fresh -> {
                                        if (!current.getAsBoolean() || !fresh.enabled() || !fresh.channels().contains(Long.toUnsignedString(channel))
                                                || !fresh.kinds().contains(notice.kind().name())) return Mono.empty();
                                        return target.getGuild().flatMap(server -> server.getSelfMember()).flatMap(bot ->
                                                Access.channel(bot, target, Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES)
                                                        .then(mentionRole(target, fresh)).flatMap(role -> {
                                                            var message = EarthquakeMessages.render(notice, fresh.silent(), role);
                                                            if (previous.messageId() != 0 && (fresh.updates().equals("edit")
                                                                    || fresh.updates().equals("compact") && previous.contentToken().equals(notice.contentToken()))) {
                                                                return target.getMessageById(Access.id(previous.messageId()))
                                                                        .flatMap(old -> old.edit(Ui.edit(message))).map(old -> old.getId().asLong())
                                                                        .onErrorResume(ClientException.class, error -> error.getStatus().code() == 404
                                                                                ? target.createMessage(message).map(sent -> sent.getId().asLong()) : Mono.error(error));
                                                            }
                                                            return target.createMessage(message).map(sent -> sent.getId().asLong());
                                                        }));
                                    })).flatMap(message -> repository.sent(lease, notice, message)).timeout(Duration.ofSeconds(45)),
                            repository::release, (lease, error) -> repository.release(lease), repository::release);
                }).thenReturn(true).onErrorResume(error -> {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Earthquake delivery failed in guild {} channel {}: {}", guild, channel,
                            error.getClass().getSimpleName()); return Mono.just(false);
                });
    }
    private Mono<Long> mentionRole(TextChannel channel, EarthquakeConfig config) {
        if (config.role() == 0) return Mono.just(0L);
        return channel.getGuild().flatMap(guild -> guild.getRoleById(Access.id(config.role())))
                .filter(role -> role.getId().asLong() != channel.getGuildId().asLong())
                .flatMap(role -> role.isMentionable() ? Mono.just(config.role()) : channel.getGuild().flatMap(guild -> guild.getSelfMember())
                        .flatMap(bot -> Access.has(bot, Permission.MENTION_EVERYONE)).map(allowed -> allowed ? config.role() : 0L)).defaultIfEmpty(0L);
    }
}
