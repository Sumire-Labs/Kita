package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class StayCommand implements Command {
    private final StayService stay;
    public StayCommand(StayService stay) { this.stay = stay; }
    @Override public CommandData definition() { return Commands.slash("stay", "ボイスチャンネルへの常駐を開始・終了"); }
    @Override public void execute(CommandContext context) {
        long channel = StayVoice.require(context.member());
        long guild = context.guild().getIdLong();
        var view = stay.toggle(guild, channel);
        if (!view.active()) { context.reply(StayPanel.render(view)); return; }
        var panel = StayPanel.render(view);
        var message = context.slash() == null ? context.channel().sendMessage(panel).complete()
                : context.slash().getHook().sendMessage(panel).complete();
        try { stay.panel(guild, view.token(), message.getChannelIdLong(), message.getIdLong()); }
        catch (IllegalArgumentException error) {
            message.editMessage(com.sumirelabs.kita.discord.Ui.edit(com.sumirelabs.kita.discord.Ui.text("Stay", "この常駐は終了しました。"))).queue();
        }
    }
}
