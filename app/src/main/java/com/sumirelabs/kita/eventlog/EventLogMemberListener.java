package com.sumirelabs.kita.eventlog;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import java.util.List;
import reactor.core.publisher.Mono;

public final class EventLogMemberListener {
    private final EventLogService service;
    private final long started = System.currentTimeMillis();
    public EventLogMemberListener(EventLogService service) { this.service = service; }
    public Mono<Void> joined(MemberJoinEvent event) {
        var member = event.getMember(); long guild = event.getGuildId().asLong();
        if (member.getId().asLong() == service.self()) return Mono.empty();
        long joined = member.getJoinTime().map(time -> time.toEpochMilli()).orElse(System.currentTimeMillis());
        if (joined < started - 30_000) return Mono.empty();
        var entry = new LogEntry("join:" + member.getId().asString() + ":" + joined, guild, 0, 0, 0, member.getId().asLong(), 0,
                member.isBot(), false, false, LogAction.MEMBER_JOIN, System.currentTimeMillis(),
                List.of(new LogEntry.Field("ユーザー", member.getUsername()), new LogEntry.Field("アカウント作成", member.getId().getTimestamp().toString())), "", "", "");
        return service.submit(guild, () -> service.emit(entry));
    }
    public Mono<Void> left(MemberLeaveEvent event) {
        var user = event.getUser(); long guild = event.getGuildId().asLong();
        if (user.getId().asLong() == service.self()) return Mono.empty();
        long joined = event.getMember().flatMap(member -> member.getJoinTime()).map(time -> time.toEpochMilli()).orElse(System.currentTimeMillis());
        var entry = new LogEntry("leave:" + user.getId().asString() + ":" + joined, guild, 0, 0, 0, user.getId().asLong(), 0,
                user.isBot(), false, false, LogAction.MEMBER_LEAVE, System.currentTimeMillis(), List.of(
                new LogEntry.Field("ユーザー", user.getUsername()),
                new LogEntry.Field("退出理由", "自主退出・キック・BANはこのイベントだけでは判別できません。確認できた管理操作はModerationに記録します。")), "", "", "");
        return service.submit(guild, () -> service.emit(entry));
    }
}
