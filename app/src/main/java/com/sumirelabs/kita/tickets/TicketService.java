package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.Category;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelCreateSpec;
import discord4j.core.spec.TextChannelEditSpec;
import discord4j.rest.util.Permission;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

public final class TicketService {
    private record Opening(long guild, long owner, AtomicReference<TextChannel> channel, AtomicBoolean activated) {}
    private final SettingsRepository settings;
    private final TicketRepository tickets;
    private final TicketTranscript transcripts;
    private final GatewayDiscordClient client;
    public TicketService(SettingsRepository settings, TicketRepository tickets, TicketTranscript transcripts, GatewayDiscordClient client) {
        this.settings = settings; this.tickets = tickets; this.transcripts = transcripts; this.client = client;
    }
    public Mono<Void> publish(long guild) {
        return settings.get(guild).flatMap(config -> client.getChannelById(Access.id(config.snowflake("ticket.panel"))).ofType(TextChannel.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("受付パネルの配置先を設定してください。")))
                .flatMap(channel -> channel.createMessage(TicketPanel.reception(config)))).then();
    }
    public Mono<TextChannel> open(Member member, String subject, String details) {
        long guildId = member.getGuildId().asLong(), owner = member.getId().asLong();
        return settings.get(guildId).flatMap(config -> {
            if (!config.enabled("ticket.enabled")) return Mono.error(new IllegalArgumentException("チケットは無効です。"));
            long roleId = config.snowflake("ticket.role");
            return member.getGuild().flatMap(guild -> guild.getRoleById(Access.id(roleId))
                    .filter(role -> !role.isManaged() && role.getId().asLong() != guildId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("担当ロールの設定を確認してください。")))
                    .then(client.getChannelById(Access.id(config.snowflake("ticket.category"))).ofType(Category.class)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("作成先カテゴリを設定してください。"))))
                    .flatMap(category -> Mono.usingWhen(tickets.reserve(guildId, owner, subject, roleId).flatMap(reserved -> reserved
                                    ? Mono.just(new Opening(guildId, owner, new AtomicReference<>(), new AtomicBoolean()))
                                    : Mono.error(new IllegalArgumentException("既に対応中のチケットがあります。"))),
                            opening -> guild.createTextChannel(TextChannelCreateSpec.builder().name("ticket-" + owner).parentId(category.getId())
                                            .topic("kita:ticket:" + guildId + ":" + owner)
                                            .permissionOverwrites(TicketPermissions.initial(guildId, owner, roleId, client.getSelfId().asLong())).build())
                                    .doOnNext(opening.channel::set)
                                    .flatMap(channel -> channel.createMessage(TicketPanel.controls(subject, details))
                                            .then(tickets.activate(guildId, owner, channel.getId().asLong()))
                                            .doOnSuccess(ignored -> opening.activated.set(true)).thenReturn(channel)),
                            opening -> Mono.empty(), (opening, error) -> clean(opening), this::clean)));
        });
    }
    private Mono<Void> clean(Opening opening) {
        // An unacknowledged create request may still have created a channel; recovery keeps its reservation.
        if (opening.activated.get() || opening.channel.get() == null) return Mono.empty();
        return tickets.release(opening.guild, opening.owner).flatMap(released ->
                released && opening.channel.get() != null ? opening.channel.get().delete() : Mono.empty());
    }
    public Mono<Void> claim(Member member, TextChannel channel) {
        return authorize(member, channel).then(tickets.claim(channel.getGuildId().asLong(), channel.getId().asLong(), member.getId().asLong()))
                .then(channel.createMessage(Ui.text("担当者", Ui.safe(member.getDisplayName()) + " が担当します。"))).then();
    }
    public Mono<Void> close(Member member, TextChannel channel) {
        long guild = channel.getGuildId().asLong(), id = channel.getId().asLong();
        return authorize(member, channel).flatMap(record -> tickets.beginClose(guild, id).flatMap(acquired -> {
            if (!acquired) return Mono.error(new IllegalArgumentException("既にクローズ処理中です。"));
            return TicketPermissions.change(channel, record, true).then(transcripts.save(channel))
                    .flatMap(path -> channel.edit(TextChannelEditSpec.builder().name("closed-" + id).build())
                            .flatMap(updated -> updated.createMessage(Ui.text("チケットをクローズしました", "履歴を保存しました。担当者: "
                                    + Ui.safe(member.getDisplayName()) + "\n保存ID: " + path.getFileName()))))
                    .then(tickets.close(guild, id)).onErrorResume(error -> client.withRetrievalStrategy(discord4j.core.retriever.EntityRetrievalStrategy.REST)
                            .getChannelById(channel.getId()).ofType(TextChannel.class).flatMap(fresh -> fresh.getName().startsWith("closed-")
                                    ? Mono.empty() : TicketPermissions.change(fresh, record, false).then(tickets.abortClose(guild, id)))
                            .onErrorResume(cleanError -> Mono.empty()).then(Mono.error(error)));
        }));
    }
    public Mono<TicketRecord> authorize(Member member, TextChannel channel) {
        return tickets.find(channel.getGuildId().asLong(), channel.getId().asLong())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("チケットが見つかりません。")))
                .flatMap(record -> Access.has(member, Permission.MANAGE_GUILD).flatMap(manager ->
                        manager || member.getRoleIds().contains(Access.id(record.supportRoleId())) ? Mono.just(record)
                                : Mono.error(new IllegalArgumentException("チケット担当者の権限が必要です。"))))
                .flatMap(record -> Access.channel(member, channel, Permission.VIEW_CHANNEL).thenReturn(record));
    }
}
