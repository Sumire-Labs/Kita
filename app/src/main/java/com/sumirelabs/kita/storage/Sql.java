package com.sumirelabs.kita.storage;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Result;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class Sql {
    private final ConnectionFactory source;
    Sql(ConnectionFactory source) { this.source = source; }
    <T> Mono<T> use(Function<Connection, Mono<T>> action) {
        return Mono.usingWhen(Mono.from(source.create()), action, connection -> Mono.from(connection.close()));
    }
    <T> Mono<T> transaction(Function<Connection, Mono<T>> action) {
        return Mono.usingWhen(Mono.from(source.create()), connection -> Mono.from(connection.beginTransaction()).then(Mono.defer(() -> action.apply(connection))),
                connection -> Mono.from(connection.commitTransaction())
                        .onErrorResume(error -> Mono.from(connection.close()).then(Mono.error(error)))
                        .then(Mono.from(connection.close())),
                (connection, error) -> rollback(connection), Sql::rollback);
    }
    private static Mono<Void> rollback(Connection connection) {
        return Mono.from(connection.rollbackTransaction()).onErrorResume(ignored -> Mono.empty()).then(Mono.from(connection.close()));
    }
    static Mono<Long> update(Connection connection, String query, Object... arguments) {
        return Flux.from(bind(connection, query, arguments).execute()).concatMap(Result::getRowsUpdated).reduce(0L, Long::sum);
    }
    static <T> Flux<T> rows(Connection connection, String query, Function<Row, T> map, Object... arguments) {
        return Flux.from(bind(connection, query, arguments).execute()).concatMap(result -> result.map((row, metadata) -> map.apply(row)));
    }
    private static io.r2dbc.spi.Statement bind(Connection connection, String query, Object[] arguments) {
        var statement = connection.createStatement(query);
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index] == null) statement.bindNull(index, String.class); else statement.bind(index, arguments[index]);
        }
        return statement;
    }
    Mono<Long> update(String query, Object... arguments) { return use(connection -> update(connection, query, arguments)); }
    <T> Mono<T> one(String query, Function<Row, T> map, Object... arguments) { return use(connection -> rows(connection, query, map, arguments).singleOrEmpty()); }
    <T> Flux<T> rows(String query, Function<Row, T> map, Object... arguments) {
        return Flux.usingWhen(Mono.from(source.create()), connection -> rows(connection, query, map, arguments), connection -> Mono.from(connection.close()));
    }
}
