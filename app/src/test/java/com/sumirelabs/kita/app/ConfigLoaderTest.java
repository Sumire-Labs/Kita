package com.sumirelabs.kita.app;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {
    @Test void secretPunctuationIsPreserved() {
        assertEquals("prefix$\\\"\n", ConfigLoader.substitute("prefix${TOKEN}", Map.of("TOKEN", "$\\\"\n")));
    }
    @Test void defaultsAndMissingVariablesBehaveClearly() {
        assertEquals("jdbc:mariadb://database:3306/kita", ConfigLoader.substitute("${URL:-jdbc:mariadb://database:3306/kita}", Map.of()));
        assertEquals("", ConfigLoader.substitute("${DEEPL_KEY:-}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.substitute("${TOKEN}", Map.of()));
    }
    @Test void buildVersionTokenWasReplaced() throws Exception {
        assertFalse(Kita.version().contains("@{"));
        assertFalse(Kita.version().isBlank());
    }
}
