package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import reactor.core.publisher.Mono;

public final class MusicListener {
    private final MusicService music;
    public MusicListener(MusicService music) { this.music = music; }

    public Mono<Void> component(ComponentInteractionEvent event) {
        if (!event.getCustomId().startsWith("music:") || event.getInteraction().getGuildId().isEmpty()) return Mono.empty();
        var context = new InteractionContext(event);
        return context.deferEdit().then(music.voiceOperation(context.guildId(), () -> context.member()
                .flatMap(MusicAccess::requireVoice).flatMap(voice -> {
                    var session = music.session(context.guildId());
                    if (!session.isCurrentPanel(event.getMessageId().asLong())) {
                        return Mono.error(new IllegalArgumentException("このプレイヤーは破棄済みです。新しい /player を使用してください。"));
                    }
                    Mono<Void> action;
                    if (event instanceof SelectMenuInteractionEvent select) {
                        var value = select.getValues().getFirst();
                        if (event.getCustomId().equals("music:preset")) {
                            if (value.equals("page:prev") || value.equals("page:next")) {
                                synchronized (session) {
                                    session.presetPage = Math.clamp(session.presetPage + (value.equals("page:prev") ? -1 : 1),
                                            0, Math.max(0, (music.presets().size() - 1) / PresetSelector.PAGE_SIZE));
                                }
                                action = Mono.empty();
                            } else action = music.presetNow(context.guildId(), value);
                        } else if (event.getCustomId().equals("music:volume")) {
                            action = music.actionNow(context.guildId(), "volume", Long.parseLong(value));
                        } else return Mono.empty();
                    } else action = music.actionNow(context.guildId(), event.getCustomId().substring(6), 0);
                    return action.then(Mono.defer(() -> session.isCurrentPanel(event.getMessageId().asLong())
                            ? context.edit(PlayerPanel.render(music, context.guildId())) : Mono.empty()));
                }))).onErrorResume(error -> context.followup(Ui.text("Music",
                        error instanceof IllegalArgumentException ? error.getMessage() : "音楽ノードとの通信に失敗しました。"), true));
    }
}
