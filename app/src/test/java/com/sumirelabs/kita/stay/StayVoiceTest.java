package com.sumirelabs.kita.stay;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import discord4j.common.util.Snowflake;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.AudioChannel;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class StayVoiceTest {
    @Test void eachInteractionRechecksVoiceMembershipPermissionsAndTheBotsChannel() {
        var channel = mock(AudioChannel.class); when(channel.getId()).thenReturn(Snowflake.of(10));
        var user = mock(Member.class); var bot = mock(Member.class); var guild = mock(Guild.class);
        var state = mock(VoiceState.class); var botState = mock(VoiceState.class);
        when(user.getGuild()).thenReturn(Mono.just(guild)); when(guild.getSelfMember()).thenReturn(Mono.just(bot));
        when(user.getVoiceState()).thenReturn(Mono.just(state)); when(state.getChannel()).thenReturn(Mono.just(channel));
        when(bot.getVoiceState()).thenReturn(Mono.just(botState)); when(botState.getChannelId()).thenReturn(Optional.of(Snowflake.of(10)));
        when(channel.getEffectivePermissions(user)).thenReturn(Mono.just(PermissionSet.of(Permission.VIEW_CHANNEL, Permission.CONNECT)));
        assertEquals(10, StayVoice.require(user).block());
        when(channel.getEffectivePermissions(user)).thenReturn(Mono.just(PermissionSet.none()));
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(user).block());
        when(channel.getEffectivePermissions(user)).thenReturn(Mono.just(PermissionSet.of(Permission.VIEW_CHANNEL, Permission.CONNECT)));
        when(botState.getChannelId()).thenReturn(Optional.of(Snowflake.of(20)));
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(user).block());
        when(bot.getVoiceState()).thenReturn(Mono.empty()); assertEquals(10, StayVoice.require(user).block());
        when(user.getVoiceState()).thenReturn(Mono.empty());
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(user).block());
    }
}
