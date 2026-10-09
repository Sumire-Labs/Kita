package com.sumirelabs.kita.discord;

import com.fasterxml.jackson.databind.JsonNode;
import discord4j.common.JacksonResources;
import discord4j.core.spec.MessageCreateSpec;

public final class Payloads {
    private Payloads() {}
    public static JsonNode json(MessageCreateSpec message) {
        return JacksonResources.create().getObjectMapper().valueToTree(message.asRequest().getJsonPayload());
    }
}
