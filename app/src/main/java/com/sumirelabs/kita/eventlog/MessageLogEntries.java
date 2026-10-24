package com.sumirelabs.kita.eventlog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class MessageLogEntries {
    private MessageLogEntries() {}
    static LogEntry edit(MessageSnapshot old, MessageSnapshot current) {
        var result = entry(current, LogAction.MESSAGE_EDIT, "edit:" + current.message() + ":" + current.version() + ":" + contentHash(current), List.of(
                new LogEntry.Field("変更前", old == null || old.content() == null ? "保存された本文がありません。" : preview(old.content())),
                new LogEntry.Field("変更後", current.content() == null ? "本文を保存しない設定です。" : preview(current.content())),
                new LogEntry.Field("添付ファイル", preview(attachments(current)))), "https://discord.com/channels/" + current.guild() + "/" + current.channel() + "/" + current.message());
        return withFile(result, "message-edit.txt", "変更前\n" + (old == null ? "[未取得]" : old.content())
                + "\n変更後\n" + current.content() + "\n添付\n" + attachments(current),
                old != null && old.content() != null && old.content().length() > 1000 || current.content() != null && current.content().length() > 1000
                        || attachments(current).length() > 1000);
    }
    static LogEntry deleted(long guild, long channel, long parent, long category, long id, MessageSnapshot old) {
        var snapshot = old == null ? MessageSnapshot.tombstone(guild, channel, id, 0) : old;
        var result = new LogEntry("delete:" + id, guild, channel, parent, category, snapshot.author(), 0, snapshot.bot(), false, snapshot.webhook(),
                LogAction.MESSAGE_DELETE, System.currentTimeMillis(), List.of(new LogEntry.Field("Message ID", String.valueOf(id)),
                new LogEntry.Field("本文", old == null || old.content() == null ? "保存期限切れ、未受信、または容量制限により本文を取得できませんでした。" : preview(old.content())),
                new LogEntry.Field("添付ファイル", old == null ? "不明" : preview(attachments(old)))), "", "", "");
        return withFile(result, "deleted-message.txt", old == null ? "" : old.content() + "\n" + attachments(old),
                old != null && (old.content() != null && old.content().length() > 1000 || attachments(old).length() > 1000));
    }
    static LogEntry bulk(long guild, long channel, long parent, long category, List<Long> ids, List<MessageSnapshot> snapshots, EventLogConfig config, long self) {
        var text = new StringBuilder(); int kept = 0;
        for (var snapshot : snapshots) {
            if (!config.user(snapshot.author(), snapshot.bot(), snapshot.webhook(), self)) continue;
            String line = "Message " + snapshot.message() + " / User " + snapshot.author() + "\n"
                    + (snapshot.content() == null ? "[本文なし]" : snapshot.content()) + "\n" + attachments(snapshot) + "\n\n";
            if ((text.length() + line.length()) * 4L > 256 * 1024) { text.append("[容量上限により以降を省略]\n"); break; }
            text.append(line); kept++;
        }
        var sorted = ids.stream().sorted().toList();
        return new LogEntry("bulk:" + channel + ":" + sorted, guild, channel, parent, category, 0, 0, false, false, false,
                LogAction.MESSAGE_BULK_DELETE, System.currentTimeMillis(), List.of(new LogEntry.Field("削除件数", String.valueOf(ids.size())),
                new LogEntry.Field("保存内容", kept + "件を取得。未取得・除外・期限切れの本文は含みません。")), "",
                "deleted-messages.txt", config.bodies() ? text.toString() : "");
    }
    private static String preview(String value) { return value.length() <= 1000 ? value : value.substring(0, 1000) + "…（全文は添付）"; }
    private static LogEntry withFile(LogEntry entry, String name, String text, boolean attach) {
        return new LogEntry(entry.key(), entry.guild(), entry.channel(), entry.parent(), entry.category(), entry.subject(), entry.actor(),
                entry.subjectBot(), entry.actorBot(), entry.webhook(), entry.action(), entry.time(), entry.fields(), entry.link(),
                attach ? name : "", attach ? text : "");
    }
    private static LogEntry entry(MessageSnapshot snapshot, LogAction action, String key, List<LogEntry.Field> fields, String link) {
        return new LogEntry(key, snapshot.guild(), snapshot.channel(), snapshot.parent(), snapshot.category(), snapshot.author(), 0,
                snapshot.bot(), false, snapshot.webhook(), action, System.currentTimeMillis(), fields, link, "", "");
    }
    private static String contentHash(MessageSnapshot snapshot) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    ((snapshot.content() == null ? "" : snapshot.content()) + attachments(snapshot)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static String attachments(MessageSnapshot snapshot) {
        return snapshot.attachments().isEmpty() ? "なし" : snapshot.attachments().stream()
                .map(file -> file.name() + " (" + file.size() + " bytes)\n" + file.url()).collect(java.util.stream.Collectors.joining("\n"));
    }
}
