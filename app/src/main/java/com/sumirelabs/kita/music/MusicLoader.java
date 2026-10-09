package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import java.time.Duration;
import java.util.List;

public final class MusicLoader {
    private final LavalinkClient client;
    private final PublicMusicResolver publicMusic = new PublicMusicResolver();
    public MusicLoader(LavalinkClient client) { this.client = client; }
    public List<Track> load(long guildId, String query) throws Exception {
        if (query.length() > 1000) throw new IllegalArgumentException("検索文字列が長すぎます。");
        var publicTracks = publicMusic.resolve(query);
        var link = client.getOrCreateLink(guildId);
        if (!publicTracks.isEmpty()) {
            var resolved = reactor.core.publisher.Flux.fromIterable(publicTracks)
                    .flatMapSequential(name -> link.loadItem("ytsearch:" + name).map(result -> switch (result) {
                        case SearchResult search -> search.getTracks().stream().limit(1).toList();
                        case TrackLoaded loaded -> List.of(loaded.getTrack());
                        case PlaylistLoaded loaded -> loaded.getTracks().stream().limit(1).toList();
                        default -> List.<Track>of();
                    }), 4).flatMapIterable(tracks -> tracks).collectList().block(Duration.ofMinutes(3));
            return resolved == null ? List.of() : resolved;
        }
        if (!query.startsWith("https://") && !query.matches("^(ytsearch|scsearch):.+")) query = "ytsearch:" + query;
        boolean searchQuery = query.startsWith("ytsearch:") || query.startsWith("scsearch:");
        return switch (link.loadItem(query).block(Duration.ofSeconds(20))) {
            case TrackLoaded loaded -> List.of(loaded.getTrack());
            case PlaylistLoaded loaded -> searchQuery ? loaded.getTracks().stream().limit(1).toList() : loaded.getTracks();
            case SearchResult search -> search.getTracks().stream().limit(1).toList();
            case null, default -> List.of();
        };
    }
}
