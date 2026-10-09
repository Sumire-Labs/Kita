package com.sumirelabs.kita.translation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagLanguagesTest {
    @Test void distinguishesChineseVariants() {
        assertEquals("ZH-HANS", FlagLanguages.target("🇨🇳", Map.of()).orElseThrow());
        assertEquals("ZH-HANT", FlagLanguages.target("🇹🇼", Map.of()).orElseThrow());
    }
    @Test void permitsConfiguredCountryMapping() {
        assertEquals("FR", FlagLanguages.target("🇨🇦", Map.of("CA", "FR")).orElseThrow());
        assertEquals("JA", FlagLanguages.target("🇯🇵", Map.of()).orElseThrow());
    }
    @Test void ignoresNonFlagsAndUnmappedCountries() {
        assertTrue(FlagLanguages.target("🏳️‍🌈", Map.of()).isEmpty());
        assertTrue(FlagLanguages.target("😊", Map.of()).isEmpty());
        assertTrue(FlagLanguages.target("🇦🇶", Map.of()).isEmpty());
    }
}
