package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.settings.GuildSettings;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.rest.util.Permission;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

final class LogSharePolicy {
    private LogSharePolicy() {}
    static Set<String> ids(GuildSettings settings, String key) {
        return Arrays.stream(settings.value(key, "").split(",")).filter(id -> id.matches("[0-9]+")).collect(Collectors.toUnmodifiableSet());
    }
    static long parent(GuildMessageChannel channel) { return channel instanceof ThreadChannel thread ? thread.getParentId().map(id -> id.asLong()).orElse(0L) : 0; }
    static boolean automatic(GuildSettings settings, long channel, long parent) {
        return settings.enabled("logshare.enabled") && settings.enabled("logshare.auto") && !excluded(settings, channel, parent);
    }
    static boolean excluded(GuildSettings settings, long channel, long parent) {
        var channels = ids(settings, "logshare.excludedChannels");
        return channels.contains(String.valueOf(channel)) || channels.contains(String.valueOf(parent));
    }
    static boolean allowed(long actor, long author, boolean manager, Set<String> roles, GuildSettings settings) {
        return actor == author || manager || roles.stream().anyMatch(ids(settings, "logshare.roles")::contains);
    }
    static Mono<Void> require(GuildSettings settings, Message message, Member member, boolean automatic) {
        if (!settings.enabled("logshare.enabled")) return Mono.error(new IllegalArgumentException("LogShareは無効です。/settings から有効にしてください。"));
        if (message.getAuthor().map(user -> user.isBot()).orElse(true) || message.getWebhookId().isPresent()) {
            return Mono.error(new IllegalArgumentException("BotやWebhookの投稿は共有できません。"));
        }
        return message.getChannel().ofType(GuildMessageChannel.class).flatMap(channel -> {
            if (excluded(settings, channel.getId().asLong(), parent(channel))
                    || automatic && !automatic(settings, channel.getId().asLong(), parent(channel))) {
                return Mono.error(new IllegalArgumentException("このチャンネルではLogShareは利用できません。"));
            }
            return Access.channel(member, channel, Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY)
                    .then(Access.has(member, Permission.MANAGE_GUILD)).flatMap(manager -> {
                        var roles = member.getRoleIds().stream().map(id -> id.asString()).collect(Collectors.toSet());
                        if (!automatic && !allowed(member.getId().asLong(), message.getAuthor().orElseThrow().getId().asLong(), manager, roles, settings)) {
                            return Mono.error(new IllegalArgumentException("投稿者本人、サーバー管理者、設定された担当者のみ共有できます。"));
                        }
                        return message.getGuild().flatMap(guild -> Access.member(message.getClient(), guild.getId().asLong(),
                                message.getClient().getSelfId().asLong())).flatMap(self -> Access.channel(self, channel, Permission.VIEW_CHANNEL,
                                Permission.READ_MESSAGE_HISTORY, channel instanceof ThreadChannel ? Permission.SEND_MESSAGES_IN_THREADS : Permission.SEND_MESSAGES));
                    });
        });
    }
}
