package com.sumirelabs.kita.previews;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.ReactiveWork;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class PreviewReplacement {
    private record Sent(boolean published, boolean complete) {}
    private final Map<String, PreviewProvider> providers;
    private final Cache<String, Preview> cache = Caffeine.newBuilder().maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(5)).build();
    public PreviewReplacement(Map<String, PreviewProvider> providers) { this.providers = Map.copyOf(providers); }
    public Mono<Integer> replace(List<SocialLink> links, boolean maySuppress, Function<Preview, Mono<Void>> publish,
                                 BiFunction<SocialLink, Throwable, Mono<Void>> failed, Supplier<Mono<Void>> suppress) {
        return Flux.fromIterable(links).concatMap(link -> fetch(link).flatMap(preview -> Mono.defer(() -> publish.apply(preview))
                        .doOnSuccess(ignored -> cache.put(link.uri().toString(), preview)).thenReturn(new Sent(true, preview.replacesOriginal())))
                .onErrorResume(error -> Mono.defer(() -> failed.apply(link, error)).thenReturn(new Sent(false, false))))
                .collectList().flatMap(sent -> {
                    int count = (int) sent.stream().filter(Sent::published).count();
                    boolean complete = !sent.isEmpty() && sent.stream().allMatch(result -> result.published && result.complete);
                    return (maySuppress && complete ? Mono.defer(suppress) : Mono.<Void>empty()).thenReturn(count);
                });
    }
    private Mono<Preview> fetch(SocialLink link) {
        return Mono.defer(() -> {
            var cached = cache.getIfPresent(link.uri().toString());
            if (cached != null) return Mono.just(cached);
            return ReactiveWork.io(() -> {
                var provider = providers.get(link.platform());
                if (provider == null) throw new IllegalArgumentException("Preview provider unavailable");
                var preview = provider.fetch(link);
                if (preview == null || preview.text().isBlank() && preview.media().isEmpty()) throw new IllegalStateException("Preview contains no post content");
                return preview;
            });
        });
    }
}
