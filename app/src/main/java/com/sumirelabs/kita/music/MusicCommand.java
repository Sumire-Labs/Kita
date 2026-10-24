package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandContext;
import com.sumirelabs.kita.discord.Commands;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public final class MusicCommand implements Command {
    private final MusicService music;
    private final String name;
    public MusicCommand(MusicService music, String name) { this.music = music; this.name = name; }
    @Override public ApplicationCommandRequest definition() {
        String description = switch (name) {
            case "play" -> "音楽またはプレイリストを再生";
            case "stop" -> "再生を停止してキューをクリア";
            case "skip" -> "次の曲へ移動";
            default -> "音楽プレイヤーを表示";
        };
        return name.equals("play") ? Commands.slash(name, description,
                Commands.option("query", "URLまたは検索文字列", ApplicationCommandOption.Type.STRING, true))
                : Commands.slash(name, description);
    }
    @Override public Mono<Void> execute(CommandContext context) {
        if (name.equals("player")) return PlayerPanels.publish(context, music);
        long guild = context.guildId();
        if (name.equals("play")) {
            var query = context.option("query", "");
            if (query.isBlank()) return Mono.error(new IllegalArgumentException("URLまたは検索文字列を指定してください。"));
            return music.voiceOperation(guild, () -> context.member().flatMap(MusicAccess::requireVoice).flatMap(voice -> {
                var session = music.session(guild);
                synchronized (session) {
                    if (session.voiceChannel != 0 && session.voiceChannel != voice.getId().asLong()) {
                        return Mono.error(new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。"));
                    }
                    session.voiceChannel = voice.getId().asLong();
                }
                return music.connect(voice);
            })).then(music.enqueue(guild, query)).flatMap(added -> context.reply(MusicMessages.added(added)));
        }
        return music.voiceOperation(guild, () -> context.member().flatMap(MusicAccess::requireVoice)
                .flatMap(voice -> music.actionNow(guild, name, 0)
                        .then(name.equals("stop") && !music.staying(guild) ? voice.sendDisconnectVoiceState() : Mono.empty())))
                .then(context.reply("Music", name.equals("stop") ? "再生を停止しました。" : "次の曲へ移動しました。"));
    }
}
