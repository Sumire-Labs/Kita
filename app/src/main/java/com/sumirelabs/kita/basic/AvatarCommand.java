package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.*;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.*;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.util.List;
import reactor.core.publisher.Mono;

public final class AvatarCommand implements Command {
    @Override public ApplicationCommandRequest definition() {
        return Commands.slash("avatar", "ユーザーまたはサーバーのアバターを表示",
                Commands.option("user", "対象ユーザー", ApplicationCommandOption.Type.USER, false),
                Commands.option("server", "サーバーアバターを優先", ApplicationCommandOption.Type.BOOLEAN, false));
    }
    @Override public Mono<Void> execute(CommandContext context) {
        boolean server = context.slash() == null ? context.arguments().contains("server") : context.slash().getOptionAsBoolean("server").orElse(false);
        Mono<discord4j.core.object.entity.User> target = context.slash() == null && context.arguments().equals(List.of("server"))
                ? Mono.just(context.user()) : context.target("user");
        return target.flatMap(user -> (server ? Access.member(context.client(), context.guildId(), user.getId().asLong())
                .map(member -> member.getEffectiveAvatarUrl()) : Mono.just(user.getAvatarUrl())).flatMap(base -> {
                    String url = base + (base.contains("?") ? "&" : "?") + "size=4096";
                    return context.reply(Ui.message(List.of(Container.of(TextDisplay.of("## " + Ui.safe(user.getUsername()) + " のアバター"),
                            MediaGallery.of(MediaGalleryItem.of(UnfurledMediaItem.of(url))), ActionRow.of(Button.link(url, "原寸で開く"))))));
                }));
    }
}
