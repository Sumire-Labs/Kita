package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import java.time.Instant;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;

public final class LeaderboardListener extends ListenerAdapter {
    private final LevelsService levels; private final WorkExecutor worker;
    public LeaderboardListener(LevelsService levels, WorkExecutor worker) { this.levels = levels; this.worker = worker; }
    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith("levels:open:")) {
            update(event, event.getComponentId().split(":")[2], LevelPeriod.TOTAL, 0, true); return;
        }
        if (!event.getComponentId().startsWith("levels:board:")) return;
        String[] parts = event.getComponentId().split(":");
        update(event, parts[2], LevelPeriod.valueOf(parts[3]), Integer.parseInt(parts[4]), false);
    }
    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("levels:period:")) return;
        update(event, event.getComponentId().split(":")[2], LevelPeriod.valueOf(event.getValues().getFirst()), 0, false);
    }
    private void update(ComponentInteraction event, String owner, LevelPeriod period, int page, boolean open) {
        if (!event.isFromGuild() || !owner.equals(event.getUser().getId())) {
            event.reply(Ui.text("Levels", "自分の /leaderboard を開いて操作してください。")).setEphemeral(true).queue(); return;
        }
        java.util.function.Consumer<net.dv8tion.jda.api.interactions.InteractionHook> callback = hook -> {
            if (!worker.submit(() -> {
                try {
                    var member = event.getGuild().retrieveMemberById(event.getUser().getIdLong()).complete();
                    if (!member.hasPermission(event.getGuildChannel(), net.dv8tion.jda.api.Permission.VIEW_CHANNEL)
                            || !LevelConfig.from(levels.settings(event.getGuild().getIdLong())).enabled()) throw new IllegalArgumentException("このランキングは利用できません。");
                    var entries = levels.repository().leaderboard(event.getGuild().getIdLong(), period, Instant.now(), page);
                    var data = LevelMessages.leaderboard(entries, period, page, event.getUser().getIdLong());
                    if (open) hook.sendMessage(data).queue(); else hook.editOriginal(Ui.edit(data)).queue();
                } catch (Exception error) { hook.sendMessage(Ui.text("Levels", "ランキングを更新できませんでした。")).setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Levels", "現在混み合っています。")).setEphemeral(true).queue();
        };
        if (open) event.deferReply(true).queue(callback); else event.deferEdit().queue(callback);
    }
}
