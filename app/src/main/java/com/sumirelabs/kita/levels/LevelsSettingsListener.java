package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Forms;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public final class LevelsSettingsListener {
    private final SettingsRepository repository;
    private final LevelsService levels;
    public LevelsSettingsListener(SettingsRepository repository, LevelsService levels) { this.repository = repository; this.levels = levels; }
    public LevelSettingsModals modals() { return new LevelSettingsModals(repository, levels); }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (!event.getCustomId().startsWith("levels-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return context.privateReply(Ui.text("Levels", "この設定を操作する権限がありません。"));
        if (parts[2].equals("amounts")) return context.member().flatMap(member -> LevelSettingsAccess.require(member, false))
                .then(event.presentModal(Forms.modal("levels-modal:" + context.userId() + ":amounts", "XP量・間隔",
                        Forms.input("chatXp", "Chat XP（1〜10000）", 6, parts.length > 5 ? parts[3] : "20"),
                        Forms.input("cooldown", "Chat間隔・秒（10〜3600）", 6, parts.length > 5 ? parts[4] : "60"),
                        Forms.input("voiceXp", "VC XP・1分あたり（1〜10000）", 6, parts.length > 5 ? parts[5] : "5"))))
                .onErrorResume(error -> context.privateReply(Ui.text("Levels", "サーバー管理権限が必要です。")));
        if (parts[2].equals("toggle") && Set.of("enabled", "chat", "voice", "mutedVoice").contains(parts[3])) {
            return update(event, parts[3].equals("voice") || parts[3].equals("mutedVoice") ? "voice" : "chat", false, old ->
                    Map.of("levels." + parts[3], String.valueOf(!Boolean.parseBoolean(old.value("levels." + parts[3],
                            parts[3].equals("enabled") ? "false" : "true")))));
        }
        return Mono.empty();
    }
    public Mono<Void> select(SelectMenuInteractionEvent event) {
        if (!event.getCustomId().startsWith("levels-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return context.privateReply(Ui.text("Levels", "この設定を操作する権限がありません。"));
        String action = parts[2];
        if (action.equals("rewardRole")) return context.member().flatMap(member -> LevelSettingsAccess.require(member, true))
                .then(event.presentModal(Forms.modal("levels-modal:" + context.userId() + ":reward:" + event.getValues().getFirst(), "報酬レベル",
                        Forms.input("level", "必要レベル（1〜100000）", 6))))
                .onErrorResume(error -> context.privateReply(Ui.text("Levels", "サーバー管理・ロール管理権限が必要です。")));
        if (Set.of("channels", "roles", "noticeChannel").contains(action)) return update(event, action.equals("noticeChannel") ? "rewards" : "chat",
                false, old -> Map.of("levels." + action, String.join(",", event.getValues())));
        var value = event.getValues().getFirst();
        if (action.equals("page")) return update(event, value, false, old -> Map.of());
        if (action.equals("rewardMode") && Set.of("stack", "highest").contains(value)) return update(event, "rewards", true, old -> Map.of("levels.rewardsMode", value));
        if (action.equals("notice") && Set.of("off", "levels", "rewards").contains(value)) return update(event, "rewards", false, old -> Map.of("levels.notice", value));
        if (action.equals("rewardDelete")) return update(event, "rewards", true, old -> {
            var rewards = new java.util.ArrayList<>(LevelReward.read(old)); rewards.removeIf(reward -> reward.roleId() == Long.parseLong(value));
            return Map.of("levels.rewards", LevelReward.write(rewards));
        });
        return Mono.empty();
    }
    private Mono<Void> update(ComponentInteractionEvent event, String page, boolean rewards, Function<GuildSettings, Map<String, String>> changes) {
        var context = new InteractionContext(event);
        return context.deferEdit().then(LevelSettingsAccess.modify(repository, context, rewards, changes)).flatMap(updated -> {
            levels.invalidate(updated.guildId()); return context.edit(LevelsSettings.render(updated, context.userId(), page));
        }).onErrorResume(error -> context.followup(Ui.text("Levels", error instanceof IllegalArgumentException ? error.getMessage() : "設定を保存できませんでした。"), true));
    }
    static boolean owner(InteractionContext context, String[] parts) {
        return context.event().getInteraction().getGuildId().isPresent() && parts.length > 2 && context.owner(parts[1]);
    }
}
