package com.sumirelabs.kita.translation;

import java.util.Map;
import java.util.Optional;

public final class FlagLanguages {
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("JP", "JA"), Map.entry("CN", "ZH-HANS"), Map.entry("TW", "ZH-HANT"),
            Map.entry("US", "EN-US"), Map.entry("GB", "EN-GB"), Map.entry("AU", "EN-GB"),
            Map.entry("CA", "EN-US"), Map.entry("KR", "KO"), Map.entry("DE", "DE"),
            Map.entry("FR", "FR"), Map.entry("ES", "ES"), Map.entry("IT", "IT"),
            Map.entry("PT", "PT-PT"), Map.entry("BR", "PT-BR"), Map.entry("RU", "RU"),
            Map.entry("UA", "UK"), Map.entry("PL", "PL"), Map.entry("NL", "NL"),
            Map.entry("SE", "SV"), Map.entry("FI", "FI"), Map.entry("DK", "DA"),
            Map.entry("NO", "NB"), Map.entry("CZ", "CS"), Map.entry("GR", "EL"),
            Map.entry("TR", "TR"), Map.entry("ID", "ID"), Map.entry("RO", "RO"),
            Map.entry("HU", "HU"), Map.entry("BG", "BG"), Map.entry("SK", "SK"),
            Map.entry("SI", "SL"), Map.entry("LT", "LT"), Map.entry("LV", "LV"), Map.entry("EE", "ET"));
    private FlagLanguages() {}

    public static Optional<String> target(String emoji, Map<String, String> overrides) {
        var points = emoji.codePoints().toArray();
        if (points.length != 2) return Optional.empty();
        for (var point : points) if (point < 0x1F1E6 || point > 0x1F1FF) return Optional.empty();
        var country = "" + (char) ('A' + points[0] - 0x1F1E6) + (char) ('A' + points[1] - 0x1F1E6);
        return Optional.ofNullable(overrides.getOrDefault(country, DEFAULTS.get(country)));
    }
}
