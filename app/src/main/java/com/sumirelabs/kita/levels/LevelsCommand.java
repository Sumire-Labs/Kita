package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import java.time.Instant;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class LevelsCommand implements Command {
    private final LevelsService levels;
    private final ProfileImages images;
    private final String name;
    public LevelsCommand(LevelsService levels, ProfileImages images, String name) { this.levels = levels; this.images = images; this.name = name; }
    @Override public CommandData definition() {
        return name.equals("profile") ? Commands.slash("profile", "レベル・XP・サーバー順位をプロフィール画像で表示")
                .addOption(OptionType.USER, "user", "対象メンバー") : Commands.slash("leaderboard", "サーバーのXPランキング")
                .addOptions(new net.dv8tion.jda.api.interactions.commands.build.OptionData(OptionType.STRING, "period", "集計期間")
                        .addChoice("累計", "TOTAL").addChoice("週間", "WEEK").addChoice("月間", "MONTH"));
    }
    @Override public void execute(CommandContext context) throws Exception {
        long guild = context.guild().getIdLong();
        if (!LevelConfig.from(levels.settings(guild)).enabled()) throw new IllegalArgumentException("Levelsは無効です。/settings から有効にしてください。");
        if (name.equals("leaderboard")) {
            var period = LevelPeriod.parse(context.option("period", "TOTAL"));
            context.reply(LevelMessages.leaderboard(levels.repository().leaderboard(guild, period, Instant.now(), 0), period, 0, context.user().getIdLong()));
            return;
        }
        var user = context.user();
        if (context.slash() != null && context.slash().getOption("user") != null) user = context.slash().getOption("user").getAsUser();
        else if (!context.arguments().isEmpty()) user = context.jda().retrieveUserById(context.arguments().getFirst().replaceAll("[<@!>]", "")).complete();
        var member = context.guild().retrieveMember(user).complete();
        var profile = levels.repository().profile(guild, user.getIdLong());
        levels.sync(context.guild(), user.getIdLong(), profile.xp());
        context.reply(LevelMessages.profile(images.profile(member, profile), context.user().getIdLong()));
    }
}
