package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.File;
import discord4j.core.object.component.UnfurledMediaItem;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Permission;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;
import reactor.core.publisher.Mono;

final class LogShareDownloads {
    private static final String PREFIX = "logshare:download:";
    private final SettingsRepository settings;
    private final MclogsGateway gateway;
    private final Semaphore capacity = new Semaphore(4);
    LogShareDownloads(SettingsRepository settings, String version) { this.settings = settings; gateway = new MclogsGateway(version); }
    static String buttonId(String filename, String url) {
        if (!url.matches("https://mclo\\.gs/[A-Za-z0-9]{1,32}")) throw new IllegalArgumentException("ログURLが不正です。");
        String safe = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (!LogDetector.supported(safe)) safe += ".log";
        String id = url.substring(url.lastIndexOf('/') + 1);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
        if (safe.isBlank() || safe.getBytes(StandardCharsets.UTF_8).length > 64 || PREFIX.length() + id.length() + 1 + encoded.length() > 100) {
            encoded = Base64.getUrlEncoder().withoutPadding().encodeToString("log.log".getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX + id + ":" + encoded;
    }
    static String filename(String componentId) {
        String name = new String(Base64.getUrlDecoder().decode(componentId.substring(componentId.lastIndexOf(':') + 1)), StandardCharsets.UTF_8);
        if (name.isBlank() || name.getBytes(StandardCharsets.UTF_8).length > 64 || name.matches(".*[\\\\/\\p{Cntrl}].*")) throw new IllegalArgumentException("ファイル名が不正です。");
        return name;
    }
    static MessageCreateSpec file(String filename, byte[] content) {
        return Ui.file(Ui.message(List.of(Container.of(File.of(UnfurledMediaItem.of("attachment://" + filename))))), filename, content);
    }
    Mono<Void> handle(ButtonInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty() || !event.getCustomId().startsWith(PREFIX)) return Mono.empty();
        var context = new InteractionContext(event);
        return context.defer(true).then(Mono.defer(() -> {
            if (!capacity.tryAcquire()) return Mono.error(new IllegalArgumentException("現在混み合っています。"));
            return require(context).then(gateway.download(event.getCustomId().split(":", 4)[2]))
                    .flatMap(bytes -> require(context).then(context.followup(file(filename(event.getCustomId()), bytes), true)))
                    .doFinally(signal -> capacity.release());
        })).onErrorResume(error -> context.followup(Ui.text("LogShare", LogShareMessages.error(error,
                "ログを取得できませんでした。リンク先の保存期限と接続を確認してください。")), true));
    }
    private Mono<Void> require(InteractionContext context) {
        return settings.get(context.guildId()).flatMap(config -> context.channel().flatMap(channel -> {
            if (!config.enabled("logshare.enabled") || LogSharePolicy.excluded(config, channel.getId().asLong(), LogSharePolicy.parent(channel))) {
                return Mono.error(new IllegalArgumentException("このチャンネルではLogShareは利用できません。"));
            }
            return context.member().flatMap(member -> Access.channel(member, channel, Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY));
        }));
    }
}
