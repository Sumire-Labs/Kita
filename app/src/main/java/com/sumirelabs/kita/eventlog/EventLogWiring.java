package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.Events;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.AuditLogEntryCreateEvent;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.message.*;
import discord4j.core.event.domain.interaction.*;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

public final class EventLogWiring implements AutoCloseable {
    private final EventLogService service;
    private final Disposable.Composite subscriptions = Disposables.composite();
    public EventLogWiring(GatewayDiscordClient client, SettingsRepository settings, EventLogRepository repository) {
        service = new EventLogService(client, settings, repository);
        var messages = new EventLogMessageListener(service); var audit = new EventLogAuditListener(service);
        var members = new EventLogMemberListener(service); var voice = new EventLogVoiceListener(service);
        var panel = new EventLogSettingsListener(settings, service);
        subscriptions.add(Events.listen(client, MessageCreateEvent.class, messages::create));
        subscriptions.add(Events.listen(client, MessageUpdateEvent.class, messages::update));
        subscriptions.add(Events.listen(client, MessageDeleteEvent.class, messages::deleted));
        subscriptions.add(Events.listen(client, MessageBulkDeleteEvent.class, messages::bulk));
        subscriptions.add(Events.listen(client, AuditLogEntryCreateEvent.class, audit::audit));
        subscriptions.add(Events.listen(client, MemberJoinEvent.class, members::joined));
        subscriptions.add(Events.listen(client, MemberLeaveEvent.class, members::left));
        subscriptions.add(Events.listen(client, VoiceStateUpdateEvent.class, voice::voice));
        subscriptions.add(Events.listen(client, SelectMenuInteractionEvent.class, panel::select));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, panel::button));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, panel::modal));
        subscriptions.add(Events.listen(client, GuildDeleteEvent.class, event -> event.isUnavailable() ? Mono.empty() : service.purge(event.getGuildId().asLong())));
    }
    public void start() { service.start(); }
    @Override public void close() { subscriptions.dispose(); service.close(); }
}