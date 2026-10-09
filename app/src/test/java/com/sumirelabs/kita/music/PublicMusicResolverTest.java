package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class PublicMusicResolverTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void spotifyResolvesMetadataWithoutCredentials() throws Exception {
        var data = json.readTree("""
                {"props":{"pageProps":{"state":{"data":{"entity":{"type":"track",
                "title":"Song", "artists":[{"name":"Artist"}]}}}}}}
                """);
        assertEquals(java.util.List.of("Song Artist"), PublicMusicResolver.spotify(data));
    }
    @Test void appleSongLinkDoesNotImportWholeAlbum() throws Exception {
        var data = json.readTree("""
                [{"title":"first","artistName":"artist","contentDescriptor":{"kind":"song","identifiers":{"storeAdamID":"1"}}},
                 {"title":"second","artistName":"artist","contentDescriptor":{"kind":"song","identifiers":{"storeAdamID":"2"}}}]
                """);
        var result = new ArrayList<String>();
        PublicMusicResolver.apple(data, "2", result);
        assertEquals(java.util.List.of("second artist"), result);
    }
}
