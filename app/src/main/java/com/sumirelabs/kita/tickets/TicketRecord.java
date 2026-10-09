package com.sumirelabs.kita.tickets;

import java.time.Instant;

public record TicketRecord(long guildId, long channelId, long ownerId, long supportRoleId, String subject,
                           String status, long assigneeId, Instant createdAt) {}
