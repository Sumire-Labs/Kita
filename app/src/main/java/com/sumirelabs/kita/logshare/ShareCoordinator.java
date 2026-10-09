package com.sumirelabs.kita.logshare;

final class ShareCoordinator {
    @FunctionalInterface interface Upload { String get() throws Exception; }
    @FunctionalInterface interface Reply { long send(String url) throws Exception; }
    private final LogShareRepository repository;
    ShareCoordinator(LogShareRepository repository) { this.repository = repository; }
    String share(LogShareRepository.Key key, Upload upload, Reply reply) throws Exception {
        var previous = repository.find(key).orElse(null);
        if (previous != null && previous.replyId() != 0) return previous.url();
        if (!repository.claim(key)) throw new IllegalArgumentException("このログは現在共有処理中です。少し待ってください。");
        try {
            var saved = repository.find(key).orElseThrow();
            String url = saved.url();
            if (url == null) { url = upload.get(); repository.uploaded(key, url); }
            repository.replied(key, reply.send(url));
            return url;
        } finally { repository.release(key); }
    }
}
