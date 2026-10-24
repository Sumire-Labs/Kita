package com.sumirelabs.kita.logshare;

import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

final class ShareCoordinator {
    private final LogShareRepository repository;
    ShareCoordinator(LogShareRepository repository) { this.repository = repository; }
    Mono<String> share(LogShareRepository.Key key, Supplier<Mono<String>> upload, Function<String, Mono<Long>> reply) {
        return repository.find(key).filter(saved -> saved.replyId() != 0).map(LogShareRepository.Share::url)
                .switchIfEmpty(Mono.usingWhen(repository.claim(key).flatMap(claimed -> claimed ? Mono.just(key)
                                : Mono.error(new IllegalArgumentException("このログは現在共有処理中です。少し待ってください。"))),
                        acquired -> repository.find(key).flatMap(saved -> saved.url() != null ? Mono.just(saved.url())
                                        : Mono.defer(upload).flatMap(url -> repository.uploaded(key, url).thenReturn(url)))
                                .flatMap(url -> reply.apply(url).flatMap(id -> repository.replied(key, id)).thenReturn(url)),
                        repository::release, (acquired, error) -> repository.release(acquired), repository::release));
    }
}
