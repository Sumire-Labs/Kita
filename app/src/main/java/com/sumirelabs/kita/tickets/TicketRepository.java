package com.sumirelabs.kita.tickets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TicketRepository {
    Mono<Boolean> reserve(long guildId, long ownerId, String subject, long supportRoleId);
    Mono<Void> activate(long guildId, long ownerId, long channelId);
    Mono<Boolean> release(long guildId, long ownerId);
    Mono<TicketRecord> find(long guildId, long channelId);
    Mono<Void> claim(long guildId, long channelId, long assigneeId);
    Mono<Boolean> beginClose(long guildId, long channelId);
    Mono<Void> abortClose(long guildId, long channelId);
    Flux<TicketRecord> pending();
    Mono<Void> close(long guildId, long channelId);
}
