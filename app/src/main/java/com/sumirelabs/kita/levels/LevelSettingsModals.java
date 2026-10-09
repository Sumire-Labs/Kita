package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.util.ArrayList;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class LevelSettingsModals extends ListenerAdapter {
    private final SettingsRepository repository; private final LevelsService levels; private final WorkExecutor worker;
    private final Map<Long, Object> locks;
    public LevelSettingsModals(SettingsRepository repository, LevelsService levels, WorkExecutor worker, Map<Long, Object> locks) {
        this.repository = repository; this.levels = levels; this.worker = worker; this.locks = locks;
    }
    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("levels-modal:")) return;
        String[] parts = event.getModalId().split(":");
        if (!event.isFromGuild() || !parts[1].equals(event.getUser().getId())) {
            event.reply(Ui.text("Levels", "この設定を操作する権限がありません。")).setEphemeral(true).queue(); return;
        }
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                try {
                    var member = event.getGuild().retrieveMemberById(event.getUser().getIdLong()).complete();
                    boolean reward = parts[2].equals("reward"); LevelsSettingsListener.require(member, reward);
                    synchronized (locks.computeIfAbsent(event.getGuild().getIdLong(), ignored -> new Object())) {
                        Map<String, String> changes;
                        if (reward) {
                            var role = event.getGuild().getRoleById(parts[3]);
                            if (role == null || !member.canInteract(role)) throw new IllegalArgumentException("操作できるロールを選択してください。");
                            RewardRoles.require(event.getGuild(), role);
                            var old = repository.get(event.getGuild().getIdLong());
                            var rewards = new ArrayList<>(LevelReward.read(old));
                            rewards.removeIf(existing -> existing.roleId() == role.getIdLong());
                            rewards.add(new LevelReward(integer(event.getValue("level").getAsString(), 1, 100_000), role.getIdLong()));
                            int previousMinimum;
                            try { previousMinimum = Integer.parseInt(old.value("levels.minimumReward", "100001")); }
                            catch (NumberFormatException ignored) { previousMinimum = 100_001; }
                            int minimum = Math.min(previousMinimum, rewards.stream().mapToInt(LevelReward::level).min().orElse(100_001));
                            changes = Map.of("levels.rewards", LevelReward.write(rewards), "levels.minimumReward", String.valueOf(minimum));
                        } else if (parts[2].equals("amounts")) changes = Map.of(
                                "levels.chatXp", String.valueOf(integer(event.getValue("chatXp").getAsString(), 1, 10_000)),
                                "levels.cooldown", String.valueOf(integer(event.getValue("cooldown").getAsString(), 10, 3600)),
                                "levels.voiceXp", String.valueOf(integer(event.getValue("voiceXp").getAsString(), 1, 10_000)));
                        else throw new IllegalArgumentException("設定画面を開き直してください。");
                        var updated = repository.update(event.getGuild().getIdLong(), changes); levels.invalidate(updated.guildId());
                        hook.sendMessage(LevelsSettings.render(updated, event.getUser().getIdLong(), reward ? "rewards" : "chat")).queue();
                    }
                } catch (Exception error) { hook.sendMessage(Ui.text("Levels", error instanceof IllegalArgumentException ? error.getMessage() : "設定を保存できませんでした。")).queue(); }
            })) hook.sendMessage(Ui.text("Levels", "現在混み合っています。")).queue();
        });
    }
    static int integer(String text, int min, int max) {
        try { int value = Integer.parseInt(text.strip()); if (value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(min + "〜" + max + "の整数を入力してください。");
    }
}
