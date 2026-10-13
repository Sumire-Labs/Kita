package com.sumirelabs.kita.eventlog;

import discord4j.core.object.entity.Message;
import java.util.List;

public record MessageSnapshot(long guild, long channel, long parent, long category, long message, long author,
                              String name, boolean bot, boolean webhook, String content, List<Attachment> attachments,
                              long created, long version, long expires, boolean deleted) {
    public record Attachment(long id, String name, String url, int size) {}
    public MessageSnapshot { attachments = List.copyOf(attachments); }
    public static MessageSnapshot of(Message message, long guild, long parent, long category, EventLogConfig config, long now, long self) {
        var author = message.getAuthor();
        boolean bot = author.map(user -> user.isBot()).orElse(false), webhook = message.getWebhookId().isPresent();
        long user = author.map(value -> value.getId().asLong()).orElse(0L);
        boolean keep = config.bodies() && config.user(user, bot, webhook, self);
        return new MessageSnapshot(guild, message.getChannelId().asLong(), parent, category, message.getId().asLong(), user,
                author.map(value -> value.getUsername()).orElse("不明"), bot, webhook,
                keep ? limit(message.getContent(), 4000) : null, keep ? message.getAttachments().stream().limit(10)
                        .map(file -> new Attachment(file.getId().asLong(), limit(file.getFilename(), 200), limit(file.getUrl(), 2000), file.getSize())).toList() : List.of(),
                message.getTimestamp().toEpochMilli(), message.getEditedTimestamp().orElse(message.getTimestamp()).toEpochMilli(),
                now + config.hours() * 3_600_000L, false);
    }
    public static MessageSnapshot tombstone(long guild, long channel, long message, long expires) {
        return new MessageSnapshot(guild, channel, 0, 0, message, 0, "不明", false, false, null, List.of(), 0, 0, expires, true);
    }
    public boolean sameContent(MessageSnapshot other) {
        return java.util.Objects.equals(content, other.content) && attachmentKey().equals(other.attachmentKey());
    }
    private List<String> attachmentKey() { return attachments.stream().map(file -> file.id + ":" + file.name + ":" + file.size).toList(); }
    public static String limit(String value, int length) { return value.length() <= length ? value : value.substring(0, length) + "…"; }
}
