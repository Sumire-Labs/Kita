package com.sumirelabs.kita.app;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

public final class HealthServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    public HealthServer(BooleanSupplier healthy) throws Exception {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 8);
        server.createContext("/health", exchange -> {
            boolean ready = healthy.getAsBoolean();
            var body = (ready ? "ready\n" : "starting\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(ready ? 200 : 503, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.setExecutor(executor);
        server.start();
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
