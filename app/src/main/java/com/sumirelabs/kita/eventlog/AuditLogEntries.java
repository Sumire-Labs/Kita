package com.sumirelabs.kita.eventlog;

import discord4j.core.object.audit.AuditLogEntry;
import discord4j.discordjson.json.AuditLogChangeData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class AuditLogEntries {
    private static final Set<String> KEYS = Set.of("name", "nick", "topic", "color", "colors", "permissions", "allow", "deny", "$add", "$remove",
            "communication_disabled_until", "mute", "deaf", "icon_hash", "banner_hash", "parent_id", "nsfw", "rate_limit_per_user",
            "verification_level", "mfa_level", "default_message_notifications", "afk_channel_id", "afk_timeout", "system_channel_id");
    private AuditLogEntries() {}
    static List<LogAction> actions(AuditLogEntry entry) {
        return switch (entry.getActionType().getValue()) {
            case 1 -> List.of(LogAction.SERVER_UPDATE); case 10 -> List.of(LogAction.CHANNEL_CREATE);
            case 11 -> List.of(LogAction.CHANNEL_UPDATE); case 12 -> List.of(LogAction.CHANNEL_DELETE);
            case 13, 14, 15 -> List.of(LogAction.CHANNEL_PERMISSIONS);
            case 20 -> List.of(LogAction.KICK); case 22 -> List.of(LogAction.BAN); case 23 -> List.of(LogAction.UNBAN);
            case 25 -> List.of(LogAction.MEMBER_ROLES); case 26, 27 -> List.of(LogAction.VOICE_MODERATION);
            case 30 -> List.of(LogAction.ROLE_CREATE); case 31 -> List.of(LogAction.ROLE_UPDATE); case 32 -> List.of(LogAction.ROLE_DELETE);
            case 24 -> {
                var keys = entry.getData().changes().toOptional().orElse(List.of()).stream().map(AuditLogChangeData::key).toList();
                var actions = new ArrayList<LogAction>();
                if (keys.contains("nick")) actions.add(LogAction.NICKNAME);
                if (keys.contains("communication_disabled_until")) actions.add(LogAction.TIMEOUT);
                if (keys.contains("mute") || keys.contains("deaf")) actions.add(LogAction.VOICE_MODERATION);
                yield List.copyOf(actions);
            }
            default -> List.of();
        };
    }
    static LogEntry entry(long guild, AuditLogEntry audit, LogAction action, boolean subjectBot, boolean actorBot, long parent, long category) {
        long target = audit.getTargetId().map(value -> value.asLong()).orElse(0L);
        long actor = audit.getResponsibleUserId().map(value -> value.asLong()).orElse(0L);
        boolean channelAction = action.category() == LogAction.Category.CHANNELS;
        var fields = new ArrayList<LogEntry.Field>();
        fields.add(new LogEntry.Field("対象ID", target == 0 ? "この監査イベントには個別対象IDがありません。" : String.valueOf(target)));
        for (var change : audit.getData().changes().toOptional().orElse(List.of())) {
            if (!KEYS.contains(change.key()) || !relevant(action, change.key())) continue;
            String old = change.oldValue().toOptional().map(Object::toString).orElse("未設定");
            String current = change.newValue().toOptional().map(Object::toString).orElse("未設定／削除");
            fields.add(new LogEntry.Field(change.key(), MessageSnapshot.limit(old, 700) + "\n→ " + MessageSnapshot.limit(current, 700)));
        }
        if (audit.getData().options().toOptional().isPresent()) {
            var options = discord4j.common.JacksonResources.create().getObjectMapper().valueToTree(audit.getData().options().get());
            for (String key : List.of("count", "channel_id", "id", "type")) if (options.has(key)) fields.add(new LogEntry.Field(key, options.get(key).asText()));
        }
        audit.getReason().ifPresent(reason -> fields.add(new LogEntry.Field("理由", MessageSnapshot.limit(reason, 1000))));
        return new LogEntry("audit:" + audit.getId().asString() + ":" + action, guild, sourceChannel(audit, action), parent, category,
                switch (action.category()) { case MEMBERS, MODERATION, VOICE -> target; default -> 0; }, actor, subjectBot, actorBot, false, action,
                audit.getId().getTimestamp().toEpochMilli(), fields, "", "", "");
    }
    static long sourceChannel(AuditLogEntry audit, LogAction action) {
        if (action.category() == LogAction.Category.CHANNELS) return audit.getTargetId().map(id -> id.asLong()).orElse(0L);
        if (action.category() == LogAction.Category.VOICE && audit.getData().options().toOptional().isPresent()) {
            var options = discord4j.common.JacksonResources.create().getObjectMapper().valueToTree(audit.getData().options().get());
            return options.path("channel_id").asLong(0);
        }
        return 0;
    }
    static long oldParent(AuditLogEntry audit) {
        return audit.getData().changes().toOptional().orElse(List.of()).stream().filter(change -> change.key().equals("parent_id"))
                .map(change -> change.oldValue().toOptional().orElseGet(() -> change.newValue().toOptional().orElse(null)))
                .filter(java.util.Objects::nonNull).mapToLong(value -> value.asLong(0)).findFirst().orElse(0);
    }
    private static boolean relevant(LogAction action, String key) {
        return switch (action) {
            case NICKNAME -> key.equals("nick"); case TIMEOUT -> key.equals("communication_disabled_until");
            case VOICE_MODERATION -> key.equals("mute") || key.equals("deaf"); default -> true;
        };
    }
}
