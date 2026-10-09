package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresetSelectorTest {
    @Test void attachesDescriptionAndRetainsOpaqueId() {
        var preset = new Preset("stable-id", "Dolby Atmos", Path.of("atmos.wav"), "Virtual surround sound", "atmos.wav");
        var option = PresetSelector.option(preset);
        assertEquals("Dolby Atmos", option.getLabel());
        assertEquals("Virtual surround sound", option.getDescription());
        assertEquals("stable-id", option.getValue());
    }
    @Test void fitsDiscordLimitsAndRetainsFullerSelectedDescription() {
        String description = "Line one\n" + "Details ".repeat(30);
        var preset = new Preset("id", "Name ".repeat(30), Path.of("file.wav"), description, "file.wav");
        var option = PresetSelector.option(preset);
        assertTrue(option.getLabel().length() <= 100);
        assertTrue(option.getDescription().length() <= 100);
        assertFalse(option.getDescription().contains("\n"));
        assertTrue(option.getDescription().endsWith("…"));
        assertTrue(PresetSelector.details(List.of(preset), "id").contains("Details ".repeat(20)));
    }
    @Test void undescribedFileHasNoEmptyDescription() {
        var option = PresetSelector.option(new Preset("id", "custom.wav", Path.of("custom.wav")));
        assertNull(option.getDescription());
        assertEquals("custom.wav", option.getLabel());
    }
}
