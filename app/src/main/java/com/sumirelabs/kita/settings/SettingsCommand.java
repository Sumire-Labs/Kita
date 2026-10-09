package com.sumirelabs.kita.settings;

import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;

public final class SettingsCommand implements Command {
    private final SettingsRepository repository;
    public SettingsCommand(SettingsRepository repository) { this.repository = repository; }
    @Override public boolean privateReply() { return true; }
    @Override public CommandData definition() {
        return Commands.slash("settings", "サーバーのKita設定パネルを開く")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }
    @Override public void execute(CommandContext context) {
        if (!context.member().hasPermission(Permission.MANAGE_SERVER)) {
            context.reply("Settings", "サーバー管理権限が必要です。");
            return;
        }
        context.reply(SettingsPanel.render(repository.get(context.guild().getIdLong()), context.user().getIdLong(), "home"));
    }
}
