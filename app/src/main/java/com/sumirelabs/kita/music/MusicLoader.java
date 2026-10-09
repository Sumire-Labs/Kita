package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.ReactiveWork;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class MusicLoader {
    private final LavalinkClient client;
    private final PublicMusicResolver publicMusic = new PublicMusicResolver();
    public MusicLoader(LavalinkClient client) { this.client = client; }
    public Mono<List<Track>> load(long guildId, String query) {
        if (query.length() > 1000) return Mono.error(new IllegalArgumentException("検索文字列が長すぎます。"));
        return ReactiveWork.io(() -> publicMusic.resolve(query)).flatMap(publicTracks -> {
            var link = client.getOrCreateLink(guildId);
            if (!publicTracks.isEmpty()) return Flux.fromIterable(publicTracks)
                    .flatMapSequential(name -> link.loadItem("ytsearch:" + name).map(result -> tracks(result, true)), 4)
                    .flatMapIterable(list -> list).collectList().timeout(Duration.ofMinutes(3));
            var identifier = query.startsWith("https://") || query.matches("^(ytsearch|scsearch):.+") ? query : "ytsearch:" + query;
            boolean search = identifier.startsWith("ytsearch:") || identifier.startsWith("scsearch:");
            return link.loadItem(identifier).map(result -> tracks(result, search)).defaultIfEmpty(List.of()).timeout(Duration.ofSeconds(20));
        });
    }
    private static List<Track> tracks(Object result, boolean search) {
        return switch (result) {
            case TrackLoaded loaded -> List.of(loaded.getTrack());
            case PlaylistLoaded loaded -> search ? loaded.getTracks().stream().limit(1).toList() : loaded.getTracks();
            case SearchResult found -> found.getTracks().stream().limit(1).toList();
            default -> List.of();
        };
    }
}
