package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import gs.mclo.api.Instance;
import gs.mclo.api.MclogsClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class MclogsGatewayTest {
    private static final class Api implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicReference<String> content = new AtomicReference<>();
        boolean filtersFail;
        int filterLines = 100;
        boolean missingLog;
        Api() throws Exception {
            server.createContext("/1/log/", exchange -> {
                assertTrue(exchange.getRequestURI().getQuery().contains("raw"));
                String json = missingLog ? "{\"success\":false,\"error\":\"Log not found\"}"
                        : new ObjectMapper().writeValueAsString(java.util.Map.of("id", "abc123", "content", java.util.Map.of("raw", "日本語のログ\n[hidden]")));
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(missingLog ? 404 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/1/limits", exchange -> {
                byte[] body = "{\"storageTime\":7776000,\"maxLength\":10485760,\"maxLines\":25000}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/1/filters", exchange -> {
                String filters = filtersFail ? "{\"success\":false,\"error\":\"unavailable\"}"
                        : "[{\"type\":\"trim\",\"data\":{}},{\"type\":\"limit-lines\",\"data\":{\"limit\":" + filterLines + "}},"
                        + "{\"type\":\"limit-bytes\",\"data\":{\"limit\":10485760}}]";
                byte[] body = filters.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(filtersFail ? 503 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/1/log", exchange -> {
                uploads.incrementAndGet();
                assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                assertTrue(exchange.getRequestHeaders().getFirst("User-Agent").startsWith("Kita/"));
                try (var gzip = new GZIPInputStream(exchange.getRequestBody())) {
                    var json = new ObjectMapper().readTree(gzip.readAllBytes());
                    assertEquals("Kita", json.path("source").asText()); content.set(json.path("content").asText());
                }
                byte[] body = "{\"success\":true,\"id\":\"abc123\",\"url\":\"https://mclo.gs/abc123\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
        }
        MclogsGateway gateway() { return new MclogsGateway(new MclogsClient("Kita", "test")
                .setInstance(new Instance("http://127.0.0.1:" + server.getAddress().getPort()))); }
        public void close() { server.stop(0); }
    }
    @Test void sdkUploadsGzipJsonWithUnicodeAndNoOperatorApiKey() throws Exception {
        try (var api = new Api()) {
            String text = "[12:00:00] INFO 日本語のログ\n[12:00:01] ERROR 💥";
            assertEquals("https://mclo.gs/abc123", api.gateway().upload(text));
            assertEquals(text, api.content.get()); assertEquals(1, api.uploads.get());
        }
    }
    @Test void stricterFilterLimitsRejectBeforePostInsteadOfSilentlyTruncating() throws Exception {
        try (var api = new Api()) {
            api.filterLines = 2;
            var error = assertThrows(Exception.class, () -> api.gateway().upload("one\ntwo\nthree"));
            assertTrue(LogShareMessages.error(error).contains("上限")); assertEquals(0, api.uploads.get());
        }
    }
    @Test void sdkFallbackLimitsCannotSilentlyShortenLogsWhenFiltersEndpointFails() throws Exception {
        try (var api = new Api()) {
            api.filtersFail = true;
            var error = assertThrows(Exception.class, () -> api.gateway().upload("line\n".repeat(15_000)));
            assertTrue(LogShareMessages.error(error).contains("上限")); assertEquals(0, api.uploads.get());
        }
    }
    @Test void downloadsStoredContentAsUtf8AndDoesNotDownloadApiErrorsAsLogs() throws Exception {
        try (var api = new Api()) {
            assertArrayEquals("日本語のログ\n[hidden]".getBytes(StandardCharsets.UTF_8), api.gateway().download("abc123"));
            api.missingLog = true;
            assertThrows(Exception.class, () -> api.gateway().download("missing"));
            assertThrows(IllegalArgumentException.class, () -> api.gateway().download("../invalid"));
        }
    }
}
