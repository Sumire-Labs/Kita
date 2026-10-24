package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import java.util.ArrayList;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class LevelSettingsModals {
    private final SettingsRepository repository;
    private final LevelsService levels;
    public LevelSettingsModals(SettingsRepository repository, LevelsService levels) { this.repository = repository; this.levels = levels; }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (!event.getCustomId().startsWith("levels-modal:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!LevelsSettingsListener.owner(context, parts)) return context.privateReply(Ui.text("Levels", "この設定を操作する権限がありません。"));
        boolean reward = parts[2].equals("reward");
        return context.defer(true).then(Mono.defer(() -> {
            if (reward) return context.member().flatMap(member -> member.getGuild().flatMap(guild ->
                    guild.getRoleById(discord4j.common.util.Snowflake.of(parts[3]))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("操作できるロールを選択してください。")))
                            .flatMap(role -> Access.role(member, role).then(RewardRoles.require(guild, role)).then(
                                    LevelSettingsAccess.modify(repository, context, true, old -> {
                                        var rewards = new ArrayList<>(LevelReward.read(old));
                                        rewards.removeIf(existing -> existing.roleId() == role.getId().asLong());
                                        rewards.add(new LevelReward(integer(context.value("level"), 1, 100_000), role.getId().asLong()));
                                        int previousMinimum;
                                        try { previousMinimum = Integer.parseInt(old.value("levels.minimumReward", "100001")); }
                                        catch (NumberFormatException ignored) { previousMinimum = 100_001; }
                                        int minimum = Math.min(previousMinimum, rewards.stream().mapToInt(LevelReward::level).min().orElse(100_001));
                                        return Map.of("levels.rewards", LevelReward.write(rewards), "levels.minimumReward", String.valueOf(minimum));
                                    })))));
            if (!parts[2].equals("amounts")) return Mono.error(new IllegalArgumentException("設定画面を開き直してください。"));
            return LevelSettingsAccess.modify(repository, context, false, old -> Map.of(
                    "levels.chatXp", String.valueOf(integer(context.value("chatXp"), 1, 10_000)),
                    "levels.cooldown", String.valueOf(integer(context.value("cooldown"), 10, 3600)),
                    "levels.voiceXp", String.valueOf(integer(context.value("voiceXp"), 1, 10_000))));
        })).flatMap(updated -> {
            levels.invalidate(updated.guildId()); return context.followup(LevelsSettings.render(updated, context.userId(), reward ? "rewards" : "chat"), true);
        }).onErrorResume(error -> context.followup(Ui.text("Levels", error instanceof IllegalArgumentException ? error.getMessage() : "設定を保存できませんでした。"), true));
    }
    static int integer(String text, int min, int max) {
        try { int value = Integer.parseInt(text.strip()); if (value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(min + "〜" + max + "の整数を入力してください。");
    }
}
