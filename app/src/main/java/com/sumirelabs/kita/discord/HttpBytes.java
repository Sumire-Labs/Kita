package com.sumirelabs.kita.discord;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public final class HttpBytes {
    private static final HttpClient CLIENT = HttpClient.create().followRedirect(true).responseTimeout(Duration.ofSeconds(20));
    private HttpBytes() {}
    public static Mono<byte[]> get(String url, int limit) {
        if (!url.startsWith("https://")) return Mono.error(new IllegalArgumentException("HTTPSのURLが必要です。"));
        return CLIENT.get().uri(url).response((response, bytes) -> {
            if (response.status().code() != 200) return Mono.error(new IllegalStateException("Download failed: " + response.status().code()));
            return bytes.asByteArray().collect(ByteArrayOutputStream::new, (output, chunk) -> {
                if ((long) output.size() + chunk.length > limit) throw new IllegalArgumentException("ファイルが上限を超えています。");
                output.writeBytes(chunk);
            }).map(ByteArrayOutputStream::toByteArray);
        }).single().timeout(Duration.ofSeconds(25));
    }
}
