package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class MusicCommand implements Command {
    private final MusicService music;
    private final String name;
    public MusicCommand(MusicService music, String name) { this.music = music; this.name = name; }
    @Override public CommandData definition() {
        var command = Commands.slash(name, switch (name) {
            case "play" -> "音楽またはプレイリストを再生";
            case "stop" -> "再生を停止してキューをクリア";
            case "skip" -> "次の曲へ移動";
            default -> "音楽プレイヤーを表示";
        });
        if (name.equals("play")) command.addOption(OptionType.STRING, "query", "URLまたは検索文字列", true);
        return command;
    }

    @Override public void execute(CommandContext context) throws Exception {
        if (name.equals("player")) {
            PlayerPanels.publish(context, music);
            return;
        }
        var voice = MusicAccess.requireVoice(context.member());
        var guildId = context.guild().getIdLong();
        if (name.equals("play")) {
            var query = context.option("query", "");
            if (query.isBlank()) throw new IllegalArgumentException("URLまたは検索文字列を指定してください。");
            var session = music.session(guildId);
            music.voiceOperation(guildId, () -> { synchronized (session) {
                MusicAccess.requireVoice(context.member());
                if (session.voiceChannel != 0 && session.voiceChannel != voice.getIdLong()) {
                    throw new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。");
                }
                session.voiceChannel = voice.getIdLong();
                context.jda().getDirectAudioController().connect(voice);
            } });
            var added = music.enqueue(guildId, query);
            context.reply(MusicMessages.added(added));
        } else {
            music.voiceOperation(guildId, () -> {
                MusicAccess.requireVoice(context.member());
                music.action(guildId, name, 0);
                if (name.equals("stop") && !music.staying(guildId)) context.jda().getDirectAudioController().disconnect(context.guild());
            });
            context.reply("Music", name.equals("stop") ? "再生を停止しました。" : "次の曲へ移動しました。");
        }
    }
}
