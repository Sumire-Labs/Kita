package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.discord.Access;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelEditSpec;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

final class TicketPermissions {
    private TicketPermissions() {}
    private static PermissionSet access() {
        return PermissionSet.of(Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES, Permission.READ_MESSAGE_HISTORY, Permission.ATTACH_FILES);
    }
    static List<PermissionOverwrite> initial(long guild, long owner, long role, long bot) {
        return List.of(PermissionOverwrite.forRole(Access.id(guild), PermissionSet.none(), PermissionSet.of(Permission.VIEW_CHANNEL)),
                PermissionOverwrite.forMember(Access.id(owner), access(), PermissionSet.none()),
                PermissionOverwrite.forRole(Access.id(role), access(), PermissionSet.none()),
                PermissionOverwrite.forMember(Access.id(bot), PermissionSet.of(Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES,
                        Permission.READ_MESSAGE_HISTORY, Permission.MANAGE_CHANNELS, Permission.MANAGE_ROLES), PermissionSet.none()));
    }
    static Mono<Void> change(TextChannel channel, TicketRecord record, boolean locked) {
        var overwrites = new ArrayList<PermissionOverwrite>(channel.getPermissionOverwrites());
        overwrites.removeIf(overwrite -> overwrite.getType() == PermissionOverwrite.Type.MEMBER && overwrite.getTargetId().asLong() == record.ownerId()
                || overwrite.getType() == PermissionOverwrite.Type.ROLE && overwrite.getTargetId().asLong() == record.supportRoleId());
        var allow = locked ? PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY) : access();
        var deny = locked ? PermissionSet.of(Permission.SEND_MESSAGES, Permission.SEND_MESSAGES_IN_THREADS,
                Permission.CREATE_PUBLIC_THREADS, Permission.CREATE_PRIVATE_THREADS) : PermissionSet.none();
        overwrites.add(PermissionOverwrite.forMember(Access.id(record.ownerId()), allow, deny));
        overwrites.add(PermissionOverwrite.forRole(Access.id(record.supportRoleId()), allow, deny));
        return channel.edit(TextChannelEditSpec.builder().permissionOverwrites(overwrites).build()).then();
    }
}
