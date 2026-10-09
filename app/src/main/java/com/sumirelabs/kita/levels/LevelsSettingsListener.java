package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;

public final class LevelsSettingsListener extends ListenerAdapter {
    private final SettingsRepository repository; private final LevelsService levels; private final WorkExecutor worker;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    public LevelsSettingsListener(SettingsRepository repository, LevelsService levels, WorkExecutor worker) {
        this.repository = repository; this.levels = levels; this.worker = worker;
    }
    public LevelSettingsModals modals() { return new LevelSettingsModals(repository, levels, worker, locks); }
    private boolean accept(ComponentInteraction event) {
        if (!event.getComponentId().startsWith("levels-settings:")) return false;
        if (!event.isFromGuild() || !event.getComponentId().split(":")[1].equals(event.getUser().getId())
                || event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(Ui.text("Levels", "この設定を操作する権限がありません。")).setEphemeral(true).queue(); return false;
        }
        return true;
    }
    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!accept(event)) return;
        String[] parts = event.getComponentId().split(":");
        if (parts[2].equals("amounts")) {
            event.replyModal(Modal.create("levels-modal:" + event.getUser().getId() + ":amounts", "XP量・間隔")
                    .addComponents(input("chatXp", "Chat XP（1〜10000）", parts.length > 5 ? parts[3] : "20"),
                            input("cooldown", "Chat間隔・秒（10〜3600）", parts.length > 5 ? parts[4] : "60"),
                            input("voiceXp", "VC XP・1分あたり（1〜10000）", parts.length > 5 ? parts[5] : "5")).build()).queue(); return;
        }
        if (parts[2].equals("toggle") && Set.of("enabled", "chat", "voice", "mutedVoice").contains(parts[3])) {
            update(event, parts[3].equals("voice") || parts[3].equals("mutedVoice") ? "voice" : "chat", false, () -> {
                var old = repository.get(event.getGuild().getIdLong());
                boolean value = Boolean.parseBoolean(old.value("levels." + parts[3], parts[3].equals("enabled") ? "false" : "true"));
                return Map.of("levels." + parts[3], String.valueOf(!value));
            });
        }
    }
    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!accept(event)) return;
        String action = event.getComponentId().split(":")[2], value = event.getValues().getFirst();
        if (action.equals("page")) { update(event, value, false, Map::of); return; }
        if (action.equals("rewardMode") && Set.of("stack", "highest").contains(value)) {
            update(event, "rewards", true, () -> Map.of("levels.rewardsMode", value));
        } else if (action.equals("notice") && Set.of("off", "levels", "rewards").contains(value)) {
            update(event, "rewards", false, () -> Map.of("levels.notice", value));
        } else if (action.equals("rewardDelete")) update(event, "rewards", true, () -> {
            var old = new java.util.ArrayList<>(LevelReward.read(repository.get(event.getGuild().getIdLong())));
            old.removeIf(reward -> reward.roleId() == Long.parseLong(value));
            return Map.of("levels.rewards", LevelReward.write(old));
        });
    }
    @Override public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!accept(event)) return;
        String action = event.getComponentId().split(":")[2];
        if (action.equals("rewardRole")) {
            if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                event.reply(Ui.text("Levels", "ロール管理権限が必要です。")).setEphemeral(true).queue(); return;
            }
            event.replyModal(Modal.create("levels-modal:" + event.getUser().getId() + ":reward:" + event.getValues().getFirst().getId(), "報酬レベル")
                    .addComponents(input("level", "必要レベル（1〜100000）")).build()).queue(); return;
        }
        if (Set.of("channels", "roles", "noticeChannel").contains(action)) {
            String values = event.getValues().stream().map(entity -> entity.getId()).collect(java.util.stream.Collectors.joining(","));
            update(event, action.equals("noticeChannel") ? "rewards" : "chat", false, () -> Map.of("levels." + action, values));
        }
    }
    private void update(ComponentInteraction event, String page, boolean rewardEdit, Supplier<Map<String, String>> changes) {
        event.deferEdit().queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    var member = event.getGuild().retrieveMemberById(event.getUser().getIdLong()).complete();
                    require(member, rewardEdit);
                    synchronized (locks.computeIfAbsent(event.getGuild().getIdLong(), ignored -> new Object())) {
                        if (rewardEdit) for (var reward : LevelReward.read(repository.get(event.getGuild().getIdLong()))) {
                            var role = event.getGuild().getRoleById(reward.roleId());
                            if (role != null && !member.canInteract(role)) throw new IllegalArgumentException("操作できない報酬ロールがあります。");
                        }
                        var updated = repository.update(event.getGuild().getIdLong(), changes.get()); levels.invalidate(updated.guildId());
                        hook.editOriginal(Ui.edit(LevelsSettings.render(updated, event.getUser().getIdLong(), page))).queue();
                    }
                } catch (Exception error) { hook.sendMessage(Ui.text("Levels", error instanceof IllegalArgumentException ? error.getMessage() : "設定を保存できませんでした。"))
                        .setEphemeral(true).queue(); }
            })) hook.sendMessage(Ui.text("Levels", "現在混み合っています。")).setEphemeral(true).queue();
        });
    }
    static void require(Member member, boolean rewards) {
        if (!member.hasPermission(Permission.MANAGE_SERVER) || rewards && !member.hasPermission(Permission.MANAGE_ROLES)) {
            throw new IllegalArgumentException(rewards ? "サーバー管理・ロール管理権限が必要です。" : "サーバー管理権限が必要です。");
        }
    }
    static Label input(String id, String label) {
        return Label.of(label, TextInput.create(id, TextInputStyle.SHORT).setMaxLength(6).build());
    }
    static Label input(String id, String label, String value) {
        return Label.of(label, TextInput.create(id, TextInputStyle.SHORT).setMaxLength(6).setValue(value).build());
    }
}
