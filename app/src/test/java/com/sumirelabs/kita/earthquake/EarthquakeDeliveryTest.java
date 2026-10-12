package com.sumirelabs.kita.earthquake;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.rest.util.Permission;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EarthquakeDeliveryTest {
    private static final class Context implements AutoCloseable {
        final GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        final SettingsRepository settings = mock(SettingsRepository.class);
        final TextChannel channel = mock(TextChannel.class);
        final Message message = mock(Message.class);
        final Guild guild = mock(Guild.class);
        final Member bot = mock(Member.class);
        final MemoryEarthquakes repository = new MemoryEarthquakes();
        final EarthquakeDelivery delivery = new EarthquakeDelivery(client, settings, repository);
        final MockedStatic<Access> access = mockStatic(Access.class);
        Context() {
            when(client.getSelfId()).thenReturn(Snowflake.of(99)); when(client.getGuilds()).thenReturn(Flux.just(guild));
            when(guild.getId()).thenReturn(Snowflake.of(1)); when(channel.getGuildId()).thenReturn(Snowflake.of(1));
            when(channel.getGuild()).thenReturn(Mono.just(guild)); when(guild.getSelfMember()).thenReturn(Mono.just(bot));
            when(client.getChannelById(Snowflake.of(10))).thenReturn(Mono.just(channel));
            when(settings.get(1)).thenReturn(Mono.just(config("new", "")));
            when(message.getId()).thenReturn(Snowflake.of(100));
            when(channel.createMessage(any(MessageCreateSpec.class))).thenReturn(Mono.just(message));
            access.when(() -> Access.id(anyLong())).thenAnswer(call -> Snowflake.of((long) call.getArgument(0)));
            access.when(() -> Access.member(client, 1, 99)).thenReturn(Mono.just(bot));
            access.when(() -> Access.channel(bot, channel, Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES)).thenReturn(Mono.empty());
        }
        GuildSettings config(String updates, String region) {
            return new GuildSettings(1, Map.of("earthquake.enabled", "true", "earthquake.channels", "10", "earthquake.updates", updates, "earthquake.regions", region));
        }
        public void close() { access.close(); }
    }
    @Test void successfulDeliveryIsNotRepeatedAndCancellationReachesPreviousRecipient() {
        try (var context = new Context()) {
            var notice = Notices.warning(1, false);
            assertTrue(context.delivery.send(notice).block()); assertTrue(context.delivery.send(notice).block());
            verify(context.channel, times(1)).createMessage(any(MessageCreateSpec.class));
            when(context.settings.get(1)).thenReturn(Mono.just(context.config("new", "大阪府")));
            assertTrue(context.delivery.send(Notices.warning(1, true)).block());
            verify(context.channel, times(2)).createMessage(any(MessageCreateSpec.class)); assertTrue(context.repository.leases.isEmpty());
        }
    }
    @Test void cancellationWithoutOriginalAndNonMatchingRegionsNeverSend() {
        try (var context = new Context()) {
            assertTrue(context.delivery.send(Notices.warning(1, true)).block());
            when(context.settings.get(1)).thenReturn(Mono.just(context.config("new", "大阪府")));
            assertTrue(context.delivery.send(Notices.warning(1, false)).block());
            verify(context.channel, never()).createMessage(any(MessageCreateSpec.class));
        }
    }
    @Test void supersededBulletinsAreNotSentToLaterRecipients() {
        try (var context = new Context()) {
            assertTrue(context.delivery.send(Notices.warning(1, false), () -> false).block());
            verify(context.channel, never()).createMessage(any(MessageCreateSpec.class));
            assertTrue(context.repository.leases.isEmpty());
        }
    }
    @Test void aLeaseHeldByAnInterruptedSenderRemainsPendingForRetry() {
        try (var context = new Context()) {
            context.repository.busy = true;
            assertFalse(context.delivery.send(Notices.warning(1, false)).block());
            verify(context.channel, never()).createMessage(any(MessageCreateSpec.class));
            context.repository.busy = false;
            assertTrue(context.delivery.send(Notices.warning(1, false)).block());
            verify(context.channel).createMessage(any(MessageCreateSpec.class));
        }
    }
    @Test void failuresRemainRetryableAndCancellationReleasesTheLease() {
        try (var context = new Context()) {
            when(context.channel.createMessage(any(MessageCreateSpec.class))).thenReturn(Mono.error(new IllegalStateException("offline")));
            assertFalse(context.delivery.send(Notices.warning(1, false)).block()); assertTrue(context.repository.leases.isEmpty());
            when(context.channel.createMessage(any(MessageCreateSpec.class))).thenReturn(Mono.never());
            var pending = context.delivery.send(Notices.warning(1, false)).subscribe();
            assertEquals(1, context.repository.leases.size()); pending.dispose(); assertTrue(context.repository.leases.isEmpty());
            when(context.channel.createMessage(any(MessageCreateSpec.class))).thenReturn(Mono.just(context.message));
            assertTrue(context.delivery.send(Notices.warning(1, false)).block());
        }
    }
    @Test void editModeUsesTheExistingMessageAndRevokedSettingsAreCheckedBeforeSending() {
        try (var context = new Context()) {
            assertTrue(context.delivery.send(Notices.warning(1, false)).block());
            when(context.settings.get(1)).thenReturn(Mono.just(context.config("edit", "")));
            when(context.channel.getMessageById(Snowflake.of(100))).thenReturn(Mono.just(context.message));
            when(context.message.edit(any(MessageEditSpec.class))).thenReturn(Mono.just(context.message));
            assertTrue(context.delivery.send(Notices.warning(2, false)).block());
            verify(context.message).edit(any(MessageEditSpec.class)); verify(context.channel, times(1)).createMessage(any(MessageCreateSpec.class));
            when(context.settings.get(1)).thenReturn(Mono.just(context.config("new", ""))).thenReturn(Mono.just(new GuildSettings(1, Map.of())));
            assertTrue(context.delivery.send(Notices.warning(3, false)).block());
            verify(context.channel, times(1)).createMessage(any(MessageCreateSpec.class));
        }
    }
    @Test void mentionPermissionUsesTheNotificationChannelOverrides() {
        try (var context = new Context()) {
            var role = mock(discord4j.core.object.entity.Role.class);
            when(role.getId()).thenReturn(Snowflake.of(123));
            when(context.guild.getRoleById(Snowflake.of(123))).thenReturn(Mono.just(role));
            when(context.settings.get(1)).thenReturn(Mono.just(new GuildSettings(1, Map.of(
                    "earthquake.enabled", "true", "earthquake.channels", "10", "earthquake.role", "123"))));
            when(context.channel.getEffectivePermissions(context.bot)).thenReturn(Mono.just(discord4j.rest.util.PermissionSet.none()));
            var messages = new java.util.ArrayList<MessageCreateSpec>();
            when(context.channel.createMessage(any(MessageCreateSpec.class))).thenAnswer(call -> {
                messages.add(call.getArgument(0)); return Mono.just(context.message);
            });
            assertTrue(context.delivery.send(Notices.warning(1, false)).block());
            when(context.channel.getEffectivePermissions(context.bot)).thenReturn(Mono.just(discord4j.rest.util.PermissionSet.of(Permission.MENTION_EVERYONE)));
            assertTrue(context.delivery.send(Notices.warning(2, false)).block());
            assertTrue(com.sumirelabs.kita.discord.Payloads.json(messages.get(0)).path("allowed_mentions").path("roles").isEmpty());
            assertEquals("123", com.sumirelabs.kita.discord.Payloads.json(messages.get(1)).path("allowed_mentions").path("roles").get(0).asText());
        }
    }
}
