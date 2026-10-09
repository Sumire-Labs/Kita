package com.sumirelabs.kita.logshare;

import java.util.Optional;

public interface LogShareRepository {
    record Key(long guildId, long messageId, String item) {}
    record Share(String url, long replyId) {}
    Optional<Share> find(Key key);
    boolean claim(Key key);
    void uploaded(Key key, String url);
    void replied(Key key, long replyId);
    void release(Key key);
}
