package com.sumirelabs.kita.app;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
    @TempDir Path directory;
    private static final String CONFIG = """
            bot: {token: test-token}
            database: {url: 'jdbc:mariadb://localhost/kita', user: kita, password: test-password}
            deepl: {key: '', maxCharacters: 3000, flagLanguages: {HK: ZH-HANT}}
            music: {enabled: false}
            previews: {ytDlp: yt-dlp}
            dataDirectory: '${KITA_CONFIG_TEST_ABSENT:-data}'
            """;

    @Test void loadsExistingYamlWithOmittedPrimitiveDefaults() throws Exception {
        var path = Files.writeString(directory.resolve("kita.yml"), CONFIG);
        var config = ConfigLoader.load(path);
        assertEquals("test-token", config.bot().token());
        assertEquals(0, config.bot().shards());
        assertEquals(0L, config.bot().developmentGuildId());
        assertEquals("ZH-HANT", config.deepl().flagLanguages().get("HK"));
        assertEquals("off", config.music().defaultPreset());
        assertEquals("data", config.dataDirectory());
    }

    @Test void unknownYamlSettingsAreStillRejected() throws Exception {
        var path = Files.writeString(directory.resolve("typo.yml"), CONFIG + "unexpectedSetting: true\n");
        assertThrows(tools.jackson.databind.exc.UnrecognizedPropertyException.class, () -> ConfigLoader.load(path));
    }

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
