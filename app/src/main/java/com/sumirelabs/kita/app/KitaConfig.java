package com.sumirelabs.kita.app;

import java.util.Map;

public record KitaConfig(Bot bot, Database database, DeepL deepl, Music music, Previews previews, String dataDirectory) {
    public record Bot(String token, int shards, long developmentGuildId) {}
    public record Database(String url, String user, String password) {}
    public record DeepL(String key, int maxCharacters, Map<String, String> flagLanguages) {}
    public record Music(boolean enabled, String uri, String password, String presetsDirectory, String defaultPreset) {
        public Music { defaultPreset = defaultPreset == null || defaultPreset.isBlank() ? "off" : defaultPreset.strip(); }
    }
    public record Previews(String ytDlp) {}

    public void validate() {
        if (bot == null || bot.token() == null || bot.token().isBlank()) throw new IllegalArgumentException("bot.token is required");
        if (bot.shards() < 0) throw new IllegalArgumentException("bot.shards must be zero (automatic) or positive");
        if (database == null || !database.url().startsWith("jdbc:mariadb://") || database.user().isBlank()
                || database.password().isBlank()) throw new IllegalArgumentException("A MariaDB URL, user and password are required");
        if (deepl == null || deepl.key() == null || deepl.maxCharacters() < 1 || deepl.maxCharacters() > 3500
                || deepl.flagLanguages() == null) throw new IllegalArgumentException("Invalid DeepL configuration");
        if (music == null || (music.enabled() && (music.password().isBlank() || music.presetsDirectory().isBlank()
                || !java.util.Set.of("http", "https").contains(java.net.URI.create(music.uri()).getScheme())))) {
            throw new IllegalArgumentException("Invalid Lavalink configuration");
        }
        if (previews == null || previews.ytDlp().isBlank() || dataDirectory == null || dataDirectory.isBlank()) {
            throw new IllegalArgumentException("Preview executable and data directory are required");
        }
    }
}
