package com.sumirelabs.kita.logshare;

import reactor.core.publisher.Mono;

public interface LogShareRepository {
    record Key(long guildId, long messageId, String item) {}
    record Share(String url, long replyId) {}
    Mono<Share> find(Key key);
    Mono<Boolean> claim(Key key);
    Mono<Void> uploaded(Key key, String url);
    Mono<Void> replied(Key key, long replyId);
    Mono<Void> release(Key key);
}
