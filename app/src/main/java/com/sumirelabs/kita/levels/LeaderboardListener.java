package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.rest.util.Permission;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class LeaderboardListener {
    private final LevelsService levels;
    public LeaderboardListener(LevelsService levels) { this.levels = levels; }
    public Mono<Void> component(ComponentInteractionEvent event) {
        if (!event.getCustomId().startsWith("levels:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (event.getInteraction().getGuildId().isEmpty() || !context.owner(parts[2])) {
            return context.privateReply(Ui.text("Levels", "自分の /leaderboard を開いて操作してください。"));
        }
        boolean open = parts[1].equals("open");
        return (open ? context.defer(true) : context.deferEdit()).then(Mono.defer(() -> {
            var period = open ? LevelPeriod.TOTAL : event instanceof SelectMenuInteractionEvent select
                    ? LevelPeriod.valueOf(select.getValues().getFirst()) : LevelPeriod.valueOf(parts[3]);
            int page = open || event instanceof SelectMenuInteractionEvent ? 0 : Math.clamp(Integer.parseInt(parts[4]), 0, 10_000);
            return context.member().flatMap(member -> context.channel().flatMap(channel -> Access.channel(member, channel, Permission.VIEW_CHANNEL)))
                    .then(levels.settings(context.guildId())).filter(config -> LevelConfig.from(config).enabled())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("このランキングは利用できません。")))
                    .then(levels.repository().leaderboard(context.guildId(), period, Instant.now(), page))
                    .flatMap(entries -> open ? context.followup(LevelMessages.leaderboard(entries, period, page, context.userId()), true)
                            : context.edit(LevelMessages.leaderboard(entries, period, page, context.userId())));
        })).onErrorResume(error -> context.followup(Ui.text("Levels", "ランキングを更新できませんでした。"), true));
    }
}
