package com.sumirelabs.kita.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class Database implements AutoCloseable {
    private final HikariDataSource pool;

    public Database(String url, String user, String password) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(12);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setPoolName("kita-db");
        pool = new HikariDataSource(config);
        Flyway.configure().dataSource(pool).locations("classpath:db/migration").load().migrate();
    }

    public DataSource source() { return pool; }
    @Override public void close() { pool.close(); }
}
