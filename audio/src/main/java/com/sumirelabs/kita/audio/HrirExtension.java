package com.sumirelabs.kita.audio;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import dev.arbjerg.lavalink.api.AudioFilterExtension;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;
import org.springframework.stereotype.Component;

@Component
public final class HrirExtension implements AudioFilterExtension {
    private final HrirCatalog catalog;
    public HrirExtension(HrirCatalog catalog) { this.catalog = catalog; }
    @Override public String getName() { return "kitaHrir"; }
    @Override public boolean isEnabled(JsonElement data) { return !preset(data).equals("off"); }

    @Override public FloatPcmAudioFilter build(JsonElement data, AudioDataFormat format, FloatPcmAudioFilter output) {
        if (format == null || output == null || format.channelCount != 2) throw new IllegalArgumentException("HRIR output must be stereo");
        return new HrirFilter(catalog.kernel(preset(data), format.sampleRate), output);
    }

    private static String preset(JsonElement data) {
        if (!(data instanceof JsonObject object) || !(object.get("preset") instanceof JsonPrimitive value)) {
            throw new IllegalArgumentException("Expected HRIR preset ID");
        }
        return value.getContent();
    }
}
