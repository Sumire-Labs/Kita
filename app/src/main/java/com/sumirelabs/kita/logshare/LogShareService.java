package com.sumirelabs.kita.logshare;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.ReactiveWork;
import com.sumirelabs.kita.discord.Replies;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.object.entity.Message;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class LogShareService {
    private final SettingsRepository settings;
    private final LogShareRepository repository;
    private final ShareCoordinator coordinator;
    private final MclogsGateway gateway;
    private final Semaphore capacity = new Semaphore(8);
    private final Cache<String, Long> cooldown = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofSeconds(5)).build();
    LogShareService(SettingsRepository settings, LogShareRepository repository, String version) {
        this.settings = settings; this.repository = repository; coordinator = new ShareCoordinator(repository); gateway = new MclogsGateway(version);
    }
    Mono<List<String>> share(Message message, long actor, boolean automatic) {
        return require(message, actor, automatic).then(Mono.defer(() -> {
            if (!capacity.tryAcquire()) return Mono.error(new IllegalArgumentException("ログ共有が混み合っています。少し待ってください。"));
            return execute(message, actor, automatic).doFinally(signal -> capacity.release());
        }));
    }
    private Mono<List<String>> execute(Message message, long actor, boolean automatic) {
        var files = message.getAttachments().stream().filter(a -> LogDetector.supported(a.getFilename())).toList();
        if (files.size() > 5) return Mono.error(new IllegalArgumentException("一度に共有できるログは5ファイルまでです。"));
        return message.getGuild().flatMap(guild -> {
            long guildId = guild.getId().asLong();
            if (!files.isEmpty()) return Flux.fromIterable(files).concatMap(file -> {
                var key = new LogShareRepository.Key(guildId, message.getId().asLong(), file.getId().asString());
                return repository.find(key).filter(saved -> saved.replyId() != 0).map(LogShareRepository.Share::url)
                        .switchIfEmpty(LogContent.download(file)
                                .onErrorResume(IllegalArgumentException.class, error -> automatic && file.getSize() <= LogContent.MAX_BYTES
                                        ? Mono.empty() : Mono.error(error))
                                .filter(content -> !automatic || LogDetector.confident(content))
                                .flatMap(content -> publish(message, actor, automatic, key, file.getFilename(), content)));
            }).collectList();
            if (automatic) return Mono.just(List.of());
            if (!message.getAttachments().isEmpty()) return Mono.error(new IllegalArgumentException(".logまたは.txtのログを添付してください。圧縮ファイルには未対応です。"));
            String content = LogDetector.unwrap(message.getContent()).replace("\r\n", "\n").replace('\r', '\n');
            return ReactiveWork.cpu(() -> HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))))
                    .flatMap(hash -> publish(message, actor, false, new LogShareRepository.Key(guildId, message.getId().asLong(), "text:" + hash),
                            "メッセージ本文", content)).map(List::of);
        });
    }
    private Mono<String> publish(Message message, long actor, boolean automatic, LogShareRepository.Key key, String name, String content) {
        return Mono.defer(() -> {
            var previous = cooldown.asMap().putIfAbsent(key.guildId() + ":" + actor, message.getId().asLong());
            if (previous != null && previous != message.getId().asLong()) return Mono.error(new IllegalArgumentException("連続した共有は5秒ほど待ってください。"));
            return ReactiveWork.cpu(() -> { LogContent.validate(content, LogContent.MAX_BYTES, LogContent.MAX_LINES); return content; })
                    .then(coordinator.share(key, () -> require(message, actor, automatic).then(gateway.upload(content)),
                            url -> require(message, actor, automatic).then(Replies.reference(message, LogShareMessages.shared(name, url)))
                                    .map(reply -> reply.getId().asLong())));
        });
    }
    private Mono<Void> require(Message message, long actor, boolean automatic) {
        return message.getGuild().flatMap(guild -> settings.get(guild.getId().asLong())
                .flatMap(config -> Access.member(message.getClient(), guild.getId().asLong(), actor)
                        .flatMap(member -> LogSharePolicy.require(config, message, member, automatic))));
    }
}
