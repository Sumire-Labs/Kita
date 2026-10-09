package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.time.Instant;
import java.util.Arrays;
import reactor.core.publisher.Mono;

public final class LevelsCommand implements Command {
    private final LevelsService levels;
    private final ProfileImages images;
    private final String name;
    public LevelsCommand(LevelsService levels, ProfileImages images, String name) { this.levels = levels; this.images = images; this.name = name; }
    @Override public ApplicationCommandRequest definition() {
        return name.equals("profile") ? Commands.slash("profile", "レベル・XP・サーバー順位をプロフィール画像で表示",
                Commands.option("user", "対象メンバー", ApplicationCommandOption.Type.USER, false))
                : Commands.slash("leaderboard", "サーバーのXPランキング",
                        discord4j.discordjson.json.ApplicationCommandOptionData.builder()
                                .name("period").description("集計期間").type(ApplicationCommandOption.Type.STRING.getValue())
                                .choices(Arrays.stream(LevelPeriod.values()).map(period -> ApplicationCommandOptionChoiceData.builder()
                                        .name(period.label()).value(period.name()).build()).toList()).build());
    }
    @Override public Mono<Void> execute(CommandContext context) {
        long guild = context.guildId();
        return levels.settings(guild).flatMap(config -> {
            if (!LevelConfig.from(config).enabled()) return Mono.error(new IllegalArgumentException("Levelsは無効です。/settings から有効にしてください。"));
            if (name.equals("leaderboard")) {
                var period = LevelPeriod.parse(context.option("period", "TOTAL"));
                return levels.repository().leaderboard(guild, period, Instant.now(), 0)
                        .flatMap(entries -> context.reply(LevelMessages.leaderboard(entries, period, 0, context.user().getId().asLong())));
            }
            return context.target("user").flatMap(user -> Access.member(context.client(), guild, user.getId().asLong())
                    .flatMap(member -> levels.repository().profile(guild, user.getId().asLong()).flatMap(profile ->
                            context.guild().flatMap(server -> levels.sync(server, user.getId().asLong(), profile.xp()))
                                    .onErrorReturn(false).then(images.profile(member, profile))
                                    .flatMap(png -> context.reply(LevelMessages.profile(png, context.user().getId().asLong()))))));
        });
    }
}
