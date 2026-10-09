package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.modals.Modal;

public final class MusicListener extends ListenerAdapter {
    private final MusicService music;
    private final WorkExecutor worker;
    public MusicListener(MusicService music, WorkExecutor worker) { this.music = music; this.worker = worker; }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("music:") || !event.isFromGuild()) return;
        String action = event.getComponentId().substring(6);
        try { if (!action.equals("refresh")) MusicAccess.requireVoice(event.getMember()); }
        catch (IllegalArgumentException error) { event.reply(Ui.text("Music", error.getMessage())).setEphemeral(true).queue(); return; }
        if (action.equals("seek")) {
            event.replyModal(Modal.create("music-seek", "再生位置を指定")
                    .addComponents(Label.of("時刻（例: 1:30 または 90秒）", TextInput.create("position", TextInputStyle.SHORT)
                            .setMaxLength(12).build())).build()).queue();
            return;
        }
        update(event, () -> {
            var guildId = event.getGuild().getIdLong();
            switch (action) {
                case "refresh" -> { }
                case "presets-prev", "presets-next" -> {
                    var session = music.session(guildId);
                    synchronized (session) {
                        session.presetPage = Math.clamp(session.presetPage + (action.endsWith("prev") ? -1 : 1),
                                0, Math.max(0, (music.presets().size() - 1) / 24));
                    }
                }
                case "rewind", "forward" -> {
                    var player = music.player(guildId);
                    long position = player == null ? 0 : player.getPosition();
                    music.action(guildId, "seek", position + (action.equals("rewind") ? -15_000 : 15_000));
                }
                default -> {
                    music.action(guildId, action, 0);
                    if (action.equals("stop")) event.getJDA().getDirectAudioController().disconnect(event.getGuild());
                }
            }
        });
    }

    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("music:") || !event.isFromGuild()) return;
        try { MusicAccess.requireVoice(event.getMember()); }
        catch (IllegalArgumentException error) { event.reply(Ui.text("Music", error.getMessage())).setEphemeral(true).queue(); return; }
        update(event, () -> {
            var value = event.getValues().getFirst();
            if (event.getComponentId().equals("music:preset")) music.preset(event.getGuild().getIdLong(), value);
            else if (event.getComponentId().equals("music:volume")) music.action(event.getGuild().getIdLong(), "volume", Long.parseLong(value));
        });
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("music-seek") || !event.isFromGuild()) return;
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    MusicAccess.requireVoice(event.getMember());
                    music.action(event.getGuild().getIdLong(), "seek", parseTime(event.getValue("position").getAsString()));
                    hook.sendMessage(Ui.text("Music", "再生位置を変更しました。")).queue();
                } catch (IllegalArgumentException error) { hook.sendMessage(Ui.text("Music", error.getMessage())).queue(); }
                catch (Exception error) { hook.sendMessage(Ui.text("Music", "シークに失敗しました。")).queue(); }
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).queue();
        });
    }

    public static long parseTime(String text) {
        text = text.strip().replace("秒", "");
        if (!text.matches("\\d{1,6}(:[0-5]\\d)?")) throw new IllegalArgumentException("例: 1:30 または 90 と入力してください。");
        var parts = text.split(":");
        long seconds = parts.length == 1 ? Long.parseLong(parts[0]) : Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
        return seconds * 1000;
    }

    private void update(ComponentInteraction event, Runnable action) {
        event.deferEdit().queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    action.run();
                    hook.editOriginal(Ui.edit(PlayerPanel.render(music, event.getGuild().getIdLong()))).queue();
                } catch (IllegalArgumentException error) { hook.sendMessage(Ui.text("Music", error.getMessage())).setEphemeral(true).queue(); }
                catch (Exception error) { hook.sendMessage(Ui.text("Music", "音楽ノードとの通信に失敗しました。")).setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Kita", "現在混み合っています。")).setEphemeral(true).queue();
        });
    }

    @Override public void onGuildLeave(GuildLeaveEvent event) { music.forget(event.getGuild().getIdLong()); }
}
