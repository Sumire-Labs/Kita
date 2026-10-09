package com.sumirelabs.kita.logshare;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

final class LogShareService {
    private final SettingsRepository settings;
    private final LogShareRepository repository;
    private final ShareCoordinator coordinator;
    private final MclogsGateway gateway;
    private final Semaphore capacity = new Semaphore(8);
    private final Cache<String, Long> cooldown = Caffeine.newBuilder().maximumSize(20_000)
            .expireAfterWrite(Duration.ofSeconds(5)).build();
    LogShareService(SettingsRepository settings, LogShareRepository repository, String version) {
        this.settings = settings; this.repository = repository; coordinator = new ShareCoordinator(repository);
        gateway = new MclogsGateway(version);
    }
    List<String> share(Message message, Member member, boolean automatic) throws Exception {
        require(message, member, automatic);
        if (!capacity.tryAcquire()) throw new IllegalArgumentException("ログ共有が混み合っています。少し待ってください。");
        try {
            var files = message.getAttachments().stream().filter(a -> LogDetector.supported(a.getFileName())).toList();
            if (files.size() > 5) throw new IllegalArgumentException("一度に共有できるログは5ファイルまでです。");
            var urls = new ArrayList<String>();
            if (!files.isEmpty()) {
                for (var file : files) {
                    var key = new LogShareRepository.Key(message.getGuild().getIdLong(), message.getIdLong(), file.getId());
                    var saved = repository.find(key).orElse(null);
                    if (saved != null && saved.replyId() != 0) { urls.add(saved.url()); continue; }
                    String content;
                    try { content = LogContent.download(file); }
                    catch (IllegalArgumentException error) {
                        if (automatic && file.getSize() > LogContent.MAX_BYTES) throw new Failure(error);
                        if (automatic) continue;
                        throw error;
                    }
                    if (automatic && !LogDetector.confident(content)) continue;
                    urls.add(publish(message, member, automatic, key, file.getFileName(), content));
                }
            } else if (!automatic) {
                if (!message.getAttachments().isEmpty()) throw new IllegalArgumentException(".logまたは.txtのログを添付してください。圧縮ファイルには未対応です。");
                String content = LogDetector.unwrap(message.getContentRaw()).replace("\r\n", "\n").replace('\r', '\n');
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
                var key = new LogShareRepository.Key(message.getGuild().getIdLong(), message.getIdLong(), "text:" + hash);
                urls.add(publish(message, member, false, key, "メッセージ本文", content));
            }
            return urls;
        } finally { capacity.release(); }
    }
    private String publish(Message message, Member member, boolean automatic, LogShareRepository.Key key,
                           String name, String content) throws Exception {
        try {
            String actor = message.getGuild().getId() + ":" + member.getId();
            var previous = cooldown.asMap().putIfAbsent(actor, message.getIdLong());
            if (previous != null && previous != message.getIdLong()) throw new IllegalArgumentException("連続した共有は5秒ほど待ってください。");
            LogContent.validate(content, LogContent.MAX_BYTES, LogContent.MAX_LINES);
            return coordinator.share(key, () -> {
                require(message, member, automatic);
                return gateway.upload(content);
            }, url -> {
                require(message, member, automatic);
                return message.reply(LogShareMessages.shared(name, url)).mentionRepliedUser(false).complete().getIdLong();
            });
        } catch (Exception error) { if (automatic) throw new Failure(error); throw error; }
    }
    private void require(Message message, Member member, boolean automatic) {
        var config = settings.get(message.getGuild().getIdLong());
        var fresh = message.getGuild().retrieveMemberById(member.getIdLong()).complete();
        LogSharePolicy.require(config, message, fresh, automatic);
        long parent = message.getChannel() instanceof ThreadChannel thread ? thread.getParentChannel().getIdLong() : 0;
        if (automatic && !LogSharePolicy.automatic(config, message.getChannelIdLong(), parent)) {
            throw new IllegalArgumentException("このチャンネルではログの自動共有が無効です。");
        }
    }
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        Failure(Exception cause) { super(cause); }
    }
}
