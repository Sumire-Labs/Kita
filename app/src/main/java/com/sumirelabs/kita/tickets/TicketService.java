package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.tickets.TicketRepository;
import com.sumirelabs.kita.discord.Ui;
import java.util.EnumSet;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class TicketService {
    private final SettingsRepository settings;
    private final TicketRepository tickets;
    private final TicketTranscript transcripts;
    private final LongFunction<Guild> guilds;

    public TicketService(SettingsRepository settings, TicketRepository tickets, TicketTranscript transcripts,
                         LongFunction<Guild> guilds) {
        this.settings = settings; this.tickets = tickets; this.transcripts = transcripts; this.guilds = guilds;
    }

    public void publish(long guildId) {
        var config = settings.get(guildId);
        var channel = guilds.apply(guildId).getTextChannelById(config.snowflake("ticket.panel"));
        if (channel == null) throw new IllegalArgumentException("受付パネルの配置先を設定してください。");
        channel.sendMessage(TicketPanel.reception(config)).complete();
    }

    public TextChannel open(Member member, String subject, String details) {
        var guild = member.getGuild();
        var config = settings.get(guild.getIdLong());
        if (!config.enabled("ticket.enabled")) throw new IllegalArgumentException("チケットは無効です。");
        var category = guild.getCategoryById(config.snowflake("ticket.category"));
        var role = guild.getRoleById(config.snowflake("ticket.role"));
        if (category == null || role == null || role.isPublicRole() || role.isManaged()) {
            throw new IllegalArgumentException("作成先カテゴリと担当ロールの設定を確認してください。");
        }
        if (!tickets.reserve(guild.getIdLong(), member.getIdLong(), subject, role.getIdLong())) {
            throw new IllegalArgumentException("既に対応中のチケットがあります。");
        }
        TextChannel channel = null;
        try {
            var access = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                    Permission.MESSAGE_ATTACH_FILES);
            channel = guild.createTextChannel("ticket-" + member.getId(), category)
                    .clearPermissionOverrides()
                    .setTopic("kita:ticket:" + guild.getId() + ":" + member.getId())
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(member, access, null).addPermissionOverride(role, access, null)
                    .addPermissionOverride(guild.getSelfMember(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                            Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS), null)
                    .complete();
            channel.sendMessage(TicketPanel.controls(subject, details)).complete();
            tickets.activate(guild.getIdLong(), member.getIdLong(), channel.getIdLong());
            return channel;
        } catch (Exception error) {
            try { if (channel != null) channel.delete().complete(); }
            finally { tickets.release(guild.getIdLong(), member.getIdLong()); }
            throw error;
        }
    }

    public void claim(Member member, TextChannel channel) {
        authorize(member, channel);
        tickets.claim(channel.getGuild().getIdLong(), channel.getIdLong(), member.getIdLong());
        channel.sendMessage(Ui.text("担当者", Ui.safe(member.getEffectiveName()) + " が担当します。")).complete();
    }

    public void close(Member member, TextChannel channel) throws Exception {
        authorize(member, channel);
        var guildId = channel.getGuild().getIdLong();
        var record = tickets.find(guildId, channel.getIdLong()).orElseThrow();
        if (!tickets.beginClose(guildId, channel.getIdLong())) throw new IllegalArgumentException("既にクローズ処理中です。");
        try {
            channel.getManager().putMemberPermissionOverride(record.ownerId(),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                    EnumSet.of(Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS,
                            Permission.CREATE_PUBLIC_THREADS, Permission.CREATE_PRIVATE_THREADS))
                    .putRolePermissionOverride(record.supportRoleId(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                            EnumSet.of(Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS)).complete();
            var transcript = transcripts.save(channel);
            channel.getManager().setName("closed-" + channel.getId()).complete();
            channel.sendMessage(Ui.text("チケットをクローズしました", "履歴を保存しました。担当者: "
                    + Ui.safe(member.getEffectiveName()) + "\n保存ID: " + transcript.getFileName())).complete();
            tickets.close(guildId, channel.getIdLong());
        } catch (Exception error) {
            if (!channel.getName().startsWith("closed-")) TicketRecovery.restorePermissions(channel, record);
            tickets.abortClose(guildId, channel.getIdLong()); throw error;
        }
    }

    private void authorize(Member member, TextChannel channel) {
        var record = tickets.find(channel.getGuild().getIdLong(), channel.getIdLong());
        if (record.isEmpty() || (!member.hasPermission(Permission.MANAGE_SERVER)
                && member.getRoles().stream().noneMatch(role -> role.getIdLong() == record.get().supportRoleId()))) {
            throw new IllegalArgumentException("チケット担当者の権限が必要です。");
        }
    }
}
