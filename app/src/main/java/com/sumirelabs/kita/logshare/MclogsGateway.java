package com.sumirelabs.kita.logshare;

import gs.mclo.api.Log;
import gs.mclo.api.MclogsClient;
import gs.mclo.api.internal.filter.FilterList;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class MclogsGateway {
    private final MclogsClient client;
    MclogsGateway(String version) { this(new MclogsClient("Kita", version)); }
    MclogsGateway(MclogsClient client) { this.client = client; }
    byte[] download(String id) throws Exception {
        if (!id.matches("[A-Za-z0-9]{1,32}")) throw new IllegalArgumentException("ログIDが不正です。");
        var response = client.getLog(id, gs.mclo.api.data.LogField.RAW).get(20, TimeUnit.SECONDS);
        if (response.getContent() == null || response.getContent().getRaw() == null) {
            throw new IllegalArgumentException("ログの内容が見つかりません。保存期限が切れている可能性があります。");
        }
        String content = response.getContent().getRaw();
        LogContent.validate(content, LogContent.MAX_BYTES, LogContent.MAX_LINES);
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    String upload(String content) throws Exception {
        var limits = client.getLimits().get(20, TimeUnit.SECONDS);
        LogContent.validate(content, limits.getMaxLength(), limits.getMaxLines());
        var response = client.uploadLog(new CompleteLog(content)).get(45, TimeUnit.SECONDS);
        String url = response.getUrl();
        if (url == null || !url.matches("https://mclo\\.gs/[A-Za-z0-9]+")) throw new IllegalStateException("Invalid log URL");
        return url;
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
