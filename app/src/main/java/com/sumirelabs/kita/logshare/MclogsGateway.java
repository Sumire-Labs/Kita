package com.sumirelabs.kita.logshare;

import gs.mclo.api.Log;
import gs.mclo.api.MclogsClient;
import gs.mclo.api.internal.filter.FilterList;
import java.io.IOException;


final class MclogsGateway {
    private final MclogsClient client;
    MclogsGateway(String version) { this(new MclogsClient("Kita", version)); }
    MclogsGateway(MclogsClient client) { this.client = client; }
    reactor.core.publisher.Mono<byte[]> download(String id) {
        if (!id.matches("[A-Za-z0-9]{1,32}")) return reactor.core.publisher.Mono.error(new IllegalArgumentException("ログIDが不正です。"));
        return reactor.core.publisher.Mono.defer(() -> reactor.core.publisher.Mono.fromFuture(client.getLog(id, gs.mclo.api.data.LogField.RAW)))
                .timeout(java.time.Duration.ofSeconds(20)).flatMap(response -> com.sumirelabs.kita.discord.ReactiveWork.cpu(() -> {
                    if (response.getContent() == null || response.getContent().getRaw() == null) {
                        throw new IllegalArgumentException("ログの内容が見つかりません。保存期限が切れている可能性があります。");
                    }
                    String content = response.getContent().getRaw();
                    LogContent.validate(content, LogContent.MAX_BYTES, LogContent.MAX_LINES);
                    return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }));
    }
    reactor.core.publisher.Mono<String> upload(String content) {
        return reactor.core.publisher.Mono.defer(() -> reactor.core.publisher.Mono.fromFuture(client.getLimits()))
                .timeout(java.time.Duration.ofSeconds(20)).flatMap(limits -> com.sumirelabs.kita.discord.ReactiveWork.cpu(() -> {
                    LogContent.validate(content, limits.getMaxLength(), limits.getMaxLines()); return new CompleteLog(content);
                })).flatMap(log -> com.sumirelabs.kita.discord.ReactiveWork.io(() -> client.uploadLog(log)))
                .flatMap(reactor.core.publisher.Mono::fromFuture).timeout(java.time.Duration.ofSeconds(45)).map(response -> {
                    String url = response.getUrl();
                    if (url == null || !url.matches("https://mclo\\.gs/[A-Za-z0-9]+")) throw new IllegalStateException("Invalid log URL");
                    return url;
                });
    }
    // Guard the exact filter limits too, including the SDK's fallback when /filters is unavailable.
    private static final class CompleteLog extends Log {
        private final String original;
        CompleteLog(String original) { super(original); this.original = original; }
        @Override public String getContent(FilterList filters) throws IOException {
            LogContent.validate(original, filters.getMaxBytes() == null ? LogContent.MAX_BYTES : filters.getMaxBytes(),
                    filters.getMaxLines() == null ? LogContent.MAX_LINES : filters.getMaxLines());
            String filtered = original;
            for (var filter : filters.getFilters()) {
                if (!filter.getType().equals("limit-lines") && !filter.getType().equals("limit-bytes")) filtered = filter.apply(filtered);
            }
            LogContent.validate(filtered, filters.getMaxBytes() == null ? LogContent.MAX_BYTES : filters.getMaxBytes(),
                    filters.getMaxLines() == null ? LogContent.MAX_LINES : filters.getMaxLines());
            return filtered;
        }
    }
}
