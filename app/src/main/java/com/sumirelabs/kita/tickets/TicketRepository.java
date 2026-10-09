package com.sumirelabs.kita.tickets;

import java.util.Optional;

public interface TicketRepository {
    boolean reserve(long guildId, long ownerId, String subject, long supportRoleId);
    void activate(long guildId, long ownerId, long channelId);
    void release(long guildId, long ownerId);
    Optional<TicketRecord> find(long guildId, long channelId);
    void claim(long guildId, long channelId, long assigneeId);
    boolean beginClose(long guildId, long channelId);
    void abortClose(long guildId, long channelId);
    java.util.List<TicketRecord> pending();
    void close(long guildId, long channelId);
}
