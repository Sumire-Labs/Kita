package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class StayListener extends ListenerAdapter {
    private final StayService stay;
    private final WorkExecutor worker;
    public StayListener(StayService stay, WorkExecutor worker) { this.stay = stay; this.worker = worker; }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.isFromGuild() || !event.getComponentId().startsWith("stay:")) return;
        var parts = event.getComponentId().split(":", 3);
        if (parts.length != 3) return;
        try {
            long channel = StayVoice.require(event.getMember());
            stay.require(event.getGuild().getIdLong(), channel, parts[1]);
            if (parts[2].equals("duration")) { event.replyModal(StayPanel.modal(parts[1])).queue(); return; }
            if (!parts[2].equals("stop") && !parts[2].equals("unlimited")) return;
        } catch (IllegalArgumentException error) {
            event.reply(Ui.text("Stay", error.getMessage())).setEphemeral(true).queue(); return;
        }
        event.deferEdit().queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    long channel = StayVoice.require(event.getMember());
                    stay.change(event.getGuild().getIdLong(), channel, parts[1], null, parts[2].equals("stop"));
                } catch (Exception error) { hook.sendMessage(Ui.text("Stay", message(error))).setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Stay", "現在混み合っています。")).setEphemeral(true).queue();
        });
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.isFromGuild() || !event.getModalId().startsWith("stay-time:")) return;
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    long channel = StayVoice.require(event.getMember());
                    var hours = event.getValue("hours");
                    var minutes = event.getValue("minutes");
                    if (hours == null || minutes == null) throw new IllegalArgumentException("時間と分を入力してください。");
                    var duration = StaySession.duration(hours.getAsString().strip(), minutes.getAsString().strip());
                    stay.change(event.getGuild().getIdLong(), channel, event.getModalId().substring(10), duration, false);
                    hook.sendMessage(Ui.text("Stay", "今から " + duration.toHours() + " 時間 " + duration.toMinutesPart()
                            + " 分後に退出します。")).queue();
                } catch (Exception error) { hook.sendMessage(Ui.text("Stay", message(error))).queue(); }
            })) hook.sendMessage(Ui.text("Stay", "現在混み合っています。")).queue();
        });
    }
    private static String message(Exception error) {
        if (error instanceof IllegalArgumentException) return error.getMessage();
        LoggerFactory.getLogger(StayListener.class).warn("Stay interaction failed", error);
        return "常駐の変更に失敗しました。時間をおいて再試行してください。";
    }
    @Override public void onGuildLeave(GuildLeaveEvent event) { stay.forget(event.getGuild().getIdLong()); }
}
