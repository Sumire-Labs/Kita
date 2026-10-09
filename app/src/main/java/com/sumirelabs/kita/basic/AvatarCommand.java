package com.sumirelabs.kita.basic;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class AvatarCommand implements Command {
    @Override public CommandData definition() {
        return Commands.slash("avatar", "ユーザーまたはサーバーのアバターを表示")
                .addOption(OptionType.USER, "user", "対象ユーザー")
                .addOption(OptionType.BOOLEAN, "server", "サーバーアバターを優先");
    }

    @Override public void execute(CommandContext context) {
        var user = context.user();
        boolean server = false;
        if (context.slash() != null) {
            var target = context.slash().getOption("user");
            if (target != null) user = target.getAsUser();
            var mode = context.slash().getOption("server");
            server = mode != null && mode.getAsBoolean();
        } else if (!context.arguments().isEmpty()) {
            server = context.arguments().contains("server");
            if (!context.arguments().getFirst().equals("server")) {
                var id = context.arguments().getFirst().replaceAll("[<@!>]", "");
                user = context.jda().retrieveUserById(id).complete();
            }
        }
        var url = user.getEffectiveAvatarUrl();
        if (server) {
            var member = context.guild().retrieveMember(user).complete();
            if (member.getAvatarUrl() != null) url = member.getAvatarUrl();
        }
        url += "?size=4096";
        context.reply(Ui.message(List.of(Container.of(
                TextDisplay.of("## " + Ui.safe(user.getName()) + " のアバター"),
                MediaGallery.of(MediaGalleryItem.fromUrl(url)),
                ActionRow.of(Button.link(url, "原寸で開く"))))));
    }
}
