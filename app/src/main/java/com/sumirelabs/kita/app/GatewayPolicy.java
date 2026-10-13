package com.sumirelabs.kita.app;

import discord4j.core.DiscordClient;
import discord4j.common.store.Store;
import discord4j.common.store.impl.LocalStoreLayout;
import discord4j.common.store.impl.StorageBackend;
import discord4j.common.store.impl.StorageConfig;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import reactor.core.publisher.Mono;

public final class GatewayPolicy {
    private GatewayPolicy() {}
    public static Mono<IntentSet> intents(DiscordClient client) {
        var base = IntentSet.of(Intent.GUILDS, Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT,
                Intent.GUILD_MESSAGE_REACTIONS, Intent.GUILD_VOICE_STATES, Intent.GUILD_MODERATION);
        return client.getApplicationService().getCurrentApplicationInfo().map(application -> members(application.flags().toOptional().orElse(0))
                ? base.or(IntentSet.of(Intent.GUILD_MEMBERS)) : base).onErrorReturn(base);
    }
    static boolean members(int flags) { return (flags & ((1 << 14) | (1 << 15))) != 0; }
    public static Store store() {
        return Store.fromLayout(LocalStoreLayout.create(StorageConfig.builder().setMessageBackend(StorageBackend.noOp()).build()));
    }
}