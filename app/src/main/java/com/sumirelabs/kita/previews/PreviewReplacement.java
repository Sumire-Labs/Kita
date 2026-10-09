package com.sumirelabs.kita.previews;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PreviewReplacement {
    private final Map<String, PreviewProvider> providers;
    private final Cache<String, Preview> cache = Caffeine.newBuilder().maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(5)).build();
    public PreviewReplacement(Map<String, PreviewProvider> providers) { this.providers = Map.copyOf(providers); }

    public int replace(List<SocialLink> links, boolean maySuppress, Consumer<Preview> publish,
                       BiConsumer<SocialLink, Exception> failed, Runnable suppress) {
        int sent = 0;
        boolean complete = true;
        for (var link : links) {
            try {
                var provider = providers.get(link.platform());
                if (provider == null) throw new IllegalArgumentException("Preview provider unavailable");
                var preview = cache.getIfPresent(link.uri().toString());
                if (preview == null) preview = provider.fetch(link);
                if (preview == null || (preview.text().isBlank() && preview.media().isEmpty())) {
                    throw new IllegalStateException("Preview contains no post content");
                }
                publish.accept(preview);
                cache.put(link.uri().toString(), preview);
                complete &= preview.replacesOriginal();
                sent++;
            } catch (Exception error) { failed.accept(link, error); }
        }
        // Discord suppresses every preview on the source message, not individual URLs.
        if (maySuppress && complete && !links.isEmpty() && sent == links.size()) suppress.run();
        return sent;
    }
}
