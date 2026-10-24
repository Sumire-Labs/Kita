package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import discord4j.core.object.component.Button;
import org.junit.jupiter.api.Test;

class PlayerPanelTest {
    @Test void compactPlayerHasOnlyIconControlsAndTwoSelectors() {
        var message = PlayerPanel.render(new MusicSession(), null, List.of());
        var children = json(message).get("components").get(0).get("components");
        assertEquals(6, children.size());
        var transport = children.get(2).get("components");
        assertEquals(3, transport.size());
        assertEquals("music:back", transport.get(0).get("custom_id").asText());
        assertEquals("music:pause", transport.get(1).get("custom_id").asText());
        assertEquals("▶️", transport.get(1).get("emoji").get("name").asText());
        assertEquals("music:skip", transport.get(2).get("custom_id").asText());
        assertFalse(transport.get(0).has("label"));
        var volume = children.get(5).get("components").get(0).get("options");
        assertEquals(21, volume.size());
        assertTrue(volume.get(2).get("default").asBoolean());
        assertEquals("10", volume.get(2).get("value").asText());
    }

    @Test void loopModeIsVisiblySelected() {
        var session = new MusicSession();
        session.queue.toggleLoop(PlaybackQueue.Loop.TRACK);
        var children = json(PlayerPanel.render(session, null, List.of())).get("components")
                .get(0).get("components");
        var loops = children.get(3).get("components");
        assertEquals(Button.Style.PRIMARY.getValue(), loops.get(0).get("style").asInt());
        assertEquals(Button.Style.SECONDARY.getValue(), loops.get(1).get("style").asInt());
    }

    @Test void presetPagingFitsDiscordLimitsAndKeepsAllPresetsReachable() {
        var presets = new ArrayList<Preset>();
        for (int i = 0; i < 60; i++) presets.add(new Preset("id" + i, "Preset " + i, Path.of("p" + i)));
        var middle = PresetSelector.menu(presets, "id25", 1).getOptions();
        assertEquals(25, middle.size());
        assertTrue(middle.stream().anyMatch(o -> o.getValue().equals("page:prev")));
        assertTrue(middle.stream().anyMatch(o -> o.getValue().equals("page:next")));
        assertTrue(middle.stream().anyMatch(o -> o.getValue().equals("id25") && o.isDefault()));
        var last = PresetSelector.menu(presets, "id59", 2).getOptions();
        assertTrue(last.stream().anyMatch(o -> o.getValue().equals("id59")));
        assertFalse(last.stream().anyMatch(o -> o.getValue().equals("page:next")));
    }
}
