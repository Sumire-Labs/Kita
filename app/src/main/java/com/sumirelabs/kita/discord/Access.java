package com.sumirelabs.kita.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class Access {
    private Access() {}
    public static Mono<Member> member(GatewayDiscordClient client, long guild, long user) {
        return client.getRestClient().getGuildService().getGuildMember(guild, user).map(data -> new Member(client, data, guild));
    }
    public static Mono<Boolean> has(Member member, Permission permission) {
        return member.getBasePermissions().map(set -> set.contains(Permission.ADMINISTRATOR) || set.contains(permission));
    }
    public static Mono<Void> require(Member member, Permission... permissions) {
        return member.getBasePermissions().flatMap(set -> set.contains(Permission.ADMINISTRATOR) || set.containsAll(Set.of(permissions))
                ? Mono.empty() : Mono.error(new IllegalArgumentException("この操作を行う権限がありません。")));
    }
    public static Mono<Void> channel(Member member, GuildChannel channel, Permission... permissions) {
        return channel.getEffectivePermissions(member).flatMap(set -> set.contains(Permission.ADMINISTRATOR) || set.containsAll(Set.of(permissions))
                ? Mono.empty() : Mono.error(new IllegalArgumentException("このチャンネルで必要な権限がありません。")));
    }
    public static Mono<Boolean> above(Member member, Role role) {
        if (role.isManaged() || role.getId().equals(member.getGuildId())) return Mono.just(false);
        return member.getGuild().flatMap(guild -> guild.getOwnerId().equals(member.getId()) ? Mono.just(true) : member.hasHigherRoles(Set.of(role.getId())));
    }
    public static Mono<Void> role(Member member, Role role) {
        return require(member, Permission.MANAGE_ROLES).then(above(member, role)).flatMap(allowed -> allowed ? Mono.empty()
                : Mono.error(new IllegalArgumentException("自分とBotが操作できる通常のロールを選択してください。")));
    }
    public static Snowflake id(long value) { return Snowflake.of(value); }
}
