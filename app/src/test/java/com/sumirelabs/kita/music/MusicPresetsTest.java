package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MusicPresetsTest {
    @Test void resolvesDefaultByFileNameEvenWhenDisplayNameChanges() {
        var entry = new Preset("halo-id", "Aura Halo 2.0", Path.of("Aura_Halo_2.0.wav"), "", "Aura_Halo_2.0.wav");
        var presets = new MusicPresets(List.of(entry), "Aura_Halo_2.0.wav");
        assertEquals("halo-id", presets.newSession().preset);
        assertTrue(presets.valid("halo-id"));
        assertFalse(presets.valid("unknown"));
    }
    @Test void unavailableFileAndExplicitOffLeaveProcessingDisabled() {
        assertEquals("off", new MusicPresets(List.of(), "Aura_Halo_2.0.wav").newSession().preset);
        assertEquals("off", new MusicPresets(List.of(), "off").newSession().preset);
    }
    @Test void opensPageContainingDefaultAndKeepsGuildSessionsIndependent() {
        var entries = new ArrayList<Preset>();
        for (int i = 0; i < 30; i++) entries.add(new Preset("id" + i, "preset" + i, Path.of("preset" + i)));
        var presets = new MusicPresets(entries, "id29");
        var first = presets.newSession();
        assertEquals(1, first.presetPage);
        first.preset = "off";
        assertEquals("id29", presets.newSession().preset);
    }
    @Test void filterPayloadUsesTheSameSelectedPresetAsTheSession() {
        var filters = MusicPresets.filters("halo-id");
        var data = (kotlinx.serialization.json.JsonObject) filters.getPluginFilters().get("kitaHrir");
        assertEquals("halo-id", ((kotlinx.serialization.json.JsonPrimitive) data.get("preset")).getContent());
        var off = (kotlinx.serialization.json.JsonObject) MusicPresets.filters("off").getPluginFilters().get("kitaHrir");
        assertEquals("off", ((kotlinx.serialization.json.JsonPrimitive) off.get("preset")).getContent());
    }
}
