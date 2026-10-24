package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.object.entity.Member;
import discord4j.rest.util.Permission;
import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class LevelSettingsAccess {
    private LevelSettingsAccess() {}
    static Mono<Void> require(Member member, boolean rewards) {
        return rewards ? Access.require(member, Permission.MANAGE_GUILD, Permission.MANAGE_ROLES) : Access.require(member, Permission.MANAGE_GUILD);
    }
    static Mono<GuildSettings> modify(SettingsRepository repository, InteractionContext context, boolean rewardEdit,
                                      Function<GuildSettings, Map<String, String>> changes) {
        return context.member().flatMap(member -> require(member, rewardEdit).then(repository.get(context.guildId())).flatMap(snapshot -> {
            Mono<Void> validation = rewardEdit ? member.getGuild().flatMap(guild -> Flux.fromIterable(LevelReward.read(snapshot))
                    .concatMap(reward -> guild.getRoleById(Access.id(reward.roleId())).flatMap(role -> Access.role(member, role)
                            .then(RewardRoles.require(guild, role)))).then()) : Mono.empty();
            return validation.then(repository.modify(context.guildId(), current -> {
                if (rewardEdit && !current.value("levels.rewards", "[]").equals(snapshot.value("levels.rewards", "[]"))) {
                    throw new IllegalArgumentException("報酬設定が変更されました。もう一度操作してください。");
                }
                return changes.apply(current);
            }));
        }));
    }
}
