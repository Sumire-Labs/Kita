package com.sumirelabs.kita.eventlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public record LogEntry(String key, long guild, long channel, long parent, long category, long subject, long actor,
                       boolean subjectBot, boolean actorBot, boolean webhook, LogAction action, long time,
                       List<Field> fields, String link, String fileName, String fileText) {
    public record Field(String label, String value) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    public LogEntry { fields = List.copyOf(fields); }
    public String encode() { return encodeObject(this); }
    public LogEntry withoutBodies() {
        if (action.category() != LogAction.Category.MESSAGES) return this;
        var labels = java.util.Set.of("変更前", "変更後", "本文", "添付ファイル");
        return new LogEntry(key, guild, channel, parent, category, subject, actor, subjectBot, actorBot, webhook, action, time,
                fields.stream().filter(field -> !labels.contains(field.label())).toList(), link, "", "");
    }
    public static String encodeObject(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Cannot encode EventLog record", error); }
    }
    public static <T> T decode(String value, Class<T> type) {
        try { return JSON.readValue(value, type); } catch (Exception error) { throw new IllegalStateException("Cannot decode EventLog record", error); }
    }
    public LogEntry identified(long user, boolean bot, String reason) {
        var details = new java.util.ArrayList<>(fields);
        if (!reason.isBlank()) details.add(new Field("理由", MessageSnapshot.limit(reason, 1000)));
        return new LogEntry(key, guild, channel, parent, category, subject, user, subjectBot, bot, webhook, action, time, details, link, fileName, fileText);
    }
}
