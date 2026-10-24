package com.sumirelabs.kita.eventlog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import discord4j.common.JacksonResources;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.audit.AuditLogEntry;
import discord4j.discordjson.json.AuditLogEntryData;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditLogEntriesTest {
    private AuditLogEntry audit(String values) throws Exception {
        var data = JacksonResources.create().getObjectMapper().readValue("""
                {"id":"1290000000000000000","user_id":"42","target_id":"43",
                "reason":"test",%s}
                """.formatted(values), AuditLogEntryData.class);
        return new AuditLogEntry(mock(GatewayDiscordClient.class), data);
    }
    @Test void memberChangesSplitActionsWithoutLeakingUnrelatedDetails() throws Exception {
        var audit = audit("""
                "action_type":24,"changes":[
                  {"key":"nick","old_value":"before","new_value":"after"},
                  {"key":"communication_disabled_until","new_value":"2026-10-15T00:00:00Z"},
                  {"key":"mute","old_value":false,"new_value":true},
                  {"key":"token","new_value":"must-not-leak"}]
                """);
        assertEquals(List.of(LogAction.NICKNAME, LogAction.TIMEOUT, LogAction.VOICE_MODERATION), AuditLogEntries.actions(audit));
        var entry = AuditLogEntries.entry(1, audit, LogAction.NICKNAME, false, false, 0, 0);
        assertEquals(42, entry.actor()); assertEquals(43, entry.subject());
        assertTrue(entry.encode().contains("before")); assertFalse(entry.encode().contains("must-not-leak"));
        assertFalse(entry.encode().contains("2026-10-15"));
    }
    @Test void deletedChannelsKeepCategoryAndRoleTargetsAreNotUsers() throws Exception {
        var deleted = audit("\"action_type\":12,\"changes\":[{\"key\":\"parent_id\",\"old_value\":\"99\"}]");
        assertEquals(99, AuditLogEntries.oldParent(deleted));
        assertEquals(43, AuditLogEntries.sourceChannel(deleted, LogAction.CHANNEL_DELETE));
        var role = audit("\"action_type\":31,\"changes\":[{\"key\":\"name\",\"new_value\":\"role\"}]");
        assertEquals(0, AuditLogEntries.entry(1, role, LogAction.ROLE_UPDATE, false, false, 0, 0).subject());
    }
    @Test void administrativeVoiceMoveUsesReportedChannelWithoutInventingAUser() throws Exception {
        var audit = audit("\"action_type\":26,\"target_id\":null,\"options\":{\"channel_id\":\"123\",\"count\":\"2\"}");
        assertEquals(123, AuditLogEntries.sourceChannel(audit, LogAction.VOICE_MODERATION));
        assertEquals(0, AuditLogEntries.entry(1, audit, LogAction.VOICE_MODERATION, false, false, 0, 0).subject());
    }
}
