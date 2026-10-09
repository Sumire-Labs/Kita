package com.sumirelabs.kita.audio;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.common.PresetFiles;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HrirCatalog {
    private final List<PresetFiles.Preset> presets;
    private final Cache<String, ConvolutionKernel> kernels = Caffeine.newBuilder().maximumSize(16).build();

    public HrirCatalog(@Value("${kita.hrir.directory:/opt/kita/presets}") String directory) throws Exception {
        presets = PresetFiles.scan(Path.of(directory));
    }
    public List<PresetFiles.Preset> presets() { return presets; }
    public ConvolutionKernel kernel(String id, int sampleRate) {
        return kernels.get(id + ":" + sampleRate, ignored -> {
            var preset = presets.stream().filter(p -> p.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown HRIR preset"));
            try { return new ConvolutionKernel(HrirDecoder.decode(preset.path(), sampleRate)); }
            catch (Exception error) { throw new IllegalArgumentException("Cannot decode HRIR preset", error); }
        });
    }
}
