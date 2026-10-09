package com.sumirelabs.kita.stay;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import org.junit.jupiter.api.Test;

class StayVoiceTest {
    @FunctionalInterface interface Calls { Object invoke(String method); }
    private static <T> T proxy(Class<T> type, Calls calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> calls.invoke(method.getName())));
    }
    private static AudioChannelUnion channel(long id) {
        return proxy(AudioChannelUnion.class, method -> method.equals("getIdLong") ? id : null);
    }
    private static GuildVoiceState state(AtomicReference<AudioChannelUnion> channel) {
        return proxy(GuildVoiceState.class, method -> switch (method) {
            case "inAudioChannel" -> channel.get() != null;
            case "getChannel" -> channel.get();
            default -> null;
        });
    }
    @Test void eachInteractionRechecksVoiceMembershipPermissionsAndTheBotsChannel() {
        var userChannel = new AtomicReference<>(channel(10));
        var botChannel = new AtomicReference<>(channel(10));
        var permitted = new AtomicBoolean(true);
        var self = proxy(SelfMember.class, method -> method.equals("getVoiceState") ? state(botChannel) : null);
        var guild = proxy(Guild.class, method -> method.equals("getSelfMember") ? self : null);
        var member = proxy(Member.class, method -> switch (method) {
            case "getVoiceState" -> state(userChannel);
            case "getGuild" -> guild;
            case "hasPermission" -> permitted.get();
            default -> null;
        });
        assertEquals(10, StayVoice.require(member));
        permitted.set(false);
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(member));
        permitted.set(true);
        botChannel.set(channel(20));
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(member));
        botChannel.set(null);
        assertEquals(10, StayVoice.require(member), "Users can start a stay when the bot is disconnected");
        userChannel.set(null);
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(member));
        assertThrows(IllegalArgumentException.class, () -> StayVoice.require(null));
    }
}
