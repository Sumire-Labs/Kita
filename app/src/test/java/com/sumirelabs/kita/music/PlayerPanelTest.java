package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;

class PlayerPanelTest {
    @Test void compactPlayerHasOnlyIconControlsAndTwoSelectors() {
        var message = PlayerPanel.render(new MusicSession(), null, List.of());
        var children = message.toData().getArray("components").getObject(0).getArray("components");
        assertEquals(6, children.length());
        var transport = children.getObject(2).getArray("components");
        assertEquals(3, transport.length());
        assertEquals("music:back", transport.getObject(0).getString("custom_id"));
        assertEquals("music:pause", transport.getObject(1).getString("custom_id"));
        assertEquals("▶️", transport.getObject(1).getObject("emoji").getString("name"));
        assertEquals("music:skip", transport.getObject(2).getString("custom_id"));
        assertFalse(transport.getObject(0).hasKey("label"));
        var volume = children.getObject(5).getArray("components").getObject(0).getArray("options");
        assertEquals(21, volume.length());
        assertTrue(volume.getObject(2).getBoolean("default"));
        assertEquals("10", volume.getObject(2).getString("value"));
    }

    @Test void loopModeIsVisiblySelected() {
        var session = new MusicSession();
        session.queue.toggleLoop(PlaybackQueue.Loop.TRACK);
        var children = PlayerPanel.render(session, null, List.of()).toData().getArray("components")
                .getObject(0).getArray("components");
        var loops = children.getObject(3).getArray("components");
        assertEquals(ButtonStyle.PRIMARY.getKey(), loops.getObject(0).getInt("style"));
        assertEquals(ButtonStyle.SECONDARY.getKey(), loops.getObject(1).getInt("style"));
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
