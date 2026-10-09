package com.sumirelabs.kita.translation;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLClientOptions;

public final class TranslationService {
    private final DeepLClient client;
    private final int maxCharacters;

    public TranslationService(String key, int maxCharacters) {
        var options = new DeepLClientOptions();
        options.setSendPlatformInfo(false);
        options.setTimeout(java.time.Duration.ofSeconds(20));
        client = key.isBlank() ? null : new DeepLClient(key, options);
        this.maxCharacters = maxCharacters;
    }

    public boolean available() { return client != null; }
    public String translate(String source, String target) throws Exception {
        if (client == null) throw new IllegalStateException("DeepL is not configured");
        if (source.length() > maxCharacters) throw new IllegalArgumentException("Message exceeds translation limit");
        return client.translateText(source, null, target).getText();
    }
}
