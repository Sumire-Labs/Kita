package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.WorkExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class ChatXpListener extends ListenerAdapter {
    private final LevelsService levels;
    private final WorkExecutor worker;
    private final ChatXpGate gate = new ChatXpGate();
    public ChatXpListener(LevelsService levels, WorkExecutor worker) { this.levels = levels; this.worker = worker; }
    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        String text = event.getMessage().getContentRaw().strip();
        if (text.isBlank() || text.startsWith("k!") || text.startsWith("/")) return;
        worker.submit(() -> {
            var config = LevelConfig.from(levels.settings(event.getGuild().getIdLong()));
            if (!config.enabled() || !config.chat()) return;
            var now = event.getMessage().getTimeCreated().toInstant();
            if (gate.blocked(event.getGuild().getIdLong(), event.getAuthor().getIdLong(), now, null)) return;
            long parent = event.getChannel() instanceof ThreadChannel thread ? thread.getParentChannel().getIdLong() : 0;
            if (!config.channel(event.getChannel().getIdLong(), parent)) return;
            var member = event.getMember() == null ? event.getGuild().retrieveMemberById(event.getAuthor().getIdLong()).complete() : event.getMember();
            if (!config.member(member.getRoles().stream().map(role -> role.getId()).collect(Collectors.toSet()))) return;
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
                if (gate.blocked(event.getGuild().getIdLong(), event.getAuthor().getIdLong(), now, hash)) return;
                var result = levels.award(event.getGuild(), event.getAuthor().getIdLong(), LevelsRepository.Source.CHAT, config.chatXp(),
                        now, hash, 0, event.getChannel().getIdLong());
                if (result.accepted()) gate.accepted(event.getGuild().getIdLong(), event.getAuthor().getIdLong(), now, config.cooldown(), hash);
            } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        });
    }
}
