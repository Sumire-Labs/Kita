package com.sumirelabs.kita.storage;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class Database implements AutoCloseable {
    private final ConnectionPool pool;
    private final String jdbcUrl, user, password;
    public Database(String url, String user, String password) {
        this.jdbcUrl = url.replaceFirst("^r2dbc:", "jdbc:"); this.user = user; this.password = password;
        var options = ConnectionFactoryOptions.parse(url.replaceFirst("^jdbc:", "r2dbc:")).mutate()
                .option(ConnectionFactoryOptions.USER, user).option(ConnectionFactoryOptions.PASSWORD, password)
                .option(Option.valueOf("timezone"), "UTC").build();
        pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ConnectionFactories.get(options))
                .initialSize(2).maxSize(12).maxAcquireTime(Duration.ofSeconds(10)).maxIdleTime(Duration.ofMinutes(30)).build());
    }
    public Mono<Void> migrate() {
        return Mono.fromRunnable(() -> Flyway.configure().dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration").load().migrate()).subscribeOn(Schedulers.boundedElastic())
                .then(pool.warmup()).then();
    }
    public ConnectionFactory source() { return pool; }
    public Mono<Void> dispose() { return pool.disposeLater(); }
    @Override public void close() { pool.dispose(); }
}
