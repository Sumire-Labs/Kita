package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.PresetFiles.Preset;
import dev.arbjerg.lavalink.client.player.FilterBuilder;
import dev.arbjerg.lavalink.protocol.v4.Filters;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

final class MusicPresets {
    private final List<Preset> entries;
    private final String defaultId;
    private final int defaultPage;

    MusicPresets(List<Preset> entries, String configured) {
        this.entries = List.copyOf(entries);
        String requested = configured == null ? "off" : configured.strip();
        var selected = entries.stream().filter(p -> p.fileName().equalsIgnoreCase(requested) || p.id().equals(requested)).findFirst();
        defaultId = selected.map(Preset::id).orElse("off");
        defaultPage = selected.map(p -> entries.indexOf(p) / PresetSelector.PAGE_SIZE).orElse(0);
        if (!requested.isBlank() && !requested.equals("off") && selected.isEmpty()) {
            LoggerFactory.getLogger(MusicPresets.class).warn("Default HRIR preset '{}' was not found; using off", requested);
        }
    }

    List<Preset> entries() { return entries; }
    boolean valid(String id) { return id.equals("off") || entries.stream().anyMatch(p -> p.id().equals(id)); }
    MusicSession newSession() {
        var session = new MusicSession();
        session.preset = defaultId;
        session.presetPage = defaultPage;
        return session;
    }

    static Filters filters(String id) {
        return new FilterBuilder().setPluginFilter("kitaHrir", Map.of("preset", id)).build();
    }
}
