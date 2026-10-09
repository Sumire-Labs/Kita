package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;

final class LogSharePolicy {
    private LogSharePolicy() {}
    static Set<String> ids(GuildSettings settings, String key) {
        return Arrays.stream(settings.value(key, "").split(",")).filter(id -> id.matches("[0-9]+"))
                .collect(Collectors.toUnmodifiableSet());
    }
    static boolean automatic(GuildSettings settings, long channelId, long parentId) {
        return settings.enabled("logshare.enabled") && settings.enabled("logshare.auto")
                && !excluded(settings, channelId, parentId);
    }
    static boolean excluded(GuildSettings settings, long channelId, long parentId) {
        var channels = ids(settings, "logshare.excludedChannels");
        return channels.contains(String.valueOf(channelId)) || channels.contains(String.valueOf(parentId));
    }
    static boolean allowed(long actor, long author, boolean manager, Set<String> roles, GuildSettings settings) {
        return actor == author || manager || roles.stream().anyMatch(ids(settings, "logshare.roles")::contains);
    }
    static void require(GuildSettings settings, Message message, Member member, boolean automatic) {
        if (!settings.enabled("logshare.enabled")) throw new IllegalArgumentException("LogShareは無効です。/settings から有効にしてください。");
        long parent = message.getChannel() instanceof net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel thread
                ? thread.getParentChannel().getIdLong() : 0;
        if (excluded(settings, message.getChannelIdLong(), parent)) throw new IllegalArgumentException("このチャンネルではLogShareは利用できません。");
        if (message.getAuthor().isBot() || message.isWebhookMessage()) throw new IllegalArgumentException("BotやWebhookの投稿は共有できません。");
        if (member == null || !member.hasPermission(message.getGuildChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)) {
            throw new IllegalArgumentException("元のメッセージを閲覧する権限が必要です。");
        }
        if (!automatic && !allowed(member.getIdLong(), message.getAuthor().getIdLong(), member.hasPermission(Permission.MANAGE_SERVER),
                member.getRoles().stream().map(role -> role.getId()).collect(Collectors.toSet()), settings)) {
            throw new IllegalArgumentException("投稿者本人、サーバー管理者、設定された担当者のみ共有できます。");
        }
        var send = message.getChannelType().isThread() ? Permission.MESSAGE_SEND_IN_THREADS : Permission.MESSAGE_SEND;
        if (!message.getGuild().getSelfMember().hasPermission(message.getGuildChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, send)) {
            throw new IllegalArgumentException("Botに元のチャンネルの閲覧・履歴・送信権限が必要です。");
        }
    }
}
