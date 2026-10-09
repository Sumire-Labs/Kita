package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;

public final class MusicListener extends ListenerAdapter {
    private final MusicService music;
    private final WorkExecutor worker;
    public MusicListener(MusicService music, WorkExecutor worker) { this.music = music; this.worker = worker; }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("music:") || !event.isFromGuild()) return;
        String action = event.getComponentId().substring(6);
        update(event, () -> music.action(event.getGuild().getIdLong(), action, 0));
    }

    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("music:") || !event.isFromGuild()) return;
        update(event, () -> {
            var value = event.getValues().getFirst();
            long guildId = event.getGuild().getIdLong();
            if (event.getComponentId().equals("music:preset")) {
                if (value.equals("page:prev") || value.equals("page:next")) {
                    var session = music.session(guildId);
                    session.presetPage = Math.clamp(session.presetPage + (value.equals("page:prev") ? -1 : 1),
                            0, Math.max(0, (music.presets().size() - 1) / PresetSelector.PAGE_SIZE));
                } else music.preset(guildId, value);
            } else if (event.getComponentId().equals("music:volume")) music.action(guildId, "volume", Long.parseLong(value));
        });
    }

    private void requireActive(ComponentInteraction event) {
        if (!music.session(event.getGuild().getIdLong()).isCurrentPanel(event.getMessageIdLong())) {
            throw new IllegalArgumentException("このプレイヤーは破棄済みです。新しい /player を使用してください。");
        }
    }

    private void update(ComponentInteraction event, Runnable action) {
        try { requireActive(event); MusicAccess.requireVoice(event.getMember()); }
        catch (IllegalArgumentException error) { event.reply(Ui.text("Music", error.getMessage())).setEphemeral(true).queue(); return; }
        event.deferEdit().queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    var session = music.session(event.getGuild().getIdLong());
                    synchronized (session) {
                        requireActive(event);
                        MusicAccess.requireVoice(event.getMember());
                        action.run();
                        hook.editOriginal(Ui.edit(PlayerPanel.render(music, event.getGuild().getIdLong()))).queue();
                    }
                } catch (IllegalArgumentException error) { hook.sendMessage(Ui.text("Music", error.getMessage())).setEphemeral(true).queue(); }
                catch (Exception error) { hook.sendMessage(Ui.text("Music", "音楽ノードとの通信に失敗しました。")).setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).setEphemeral(true).queue();
        });
    }

    @Override public void onGuildLeave(GuildLeaveEvent event) { music.forget(event.getGuild().getIdLong()); }
}
