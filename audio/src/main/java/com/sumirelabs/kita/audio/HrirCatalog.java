package com.sumirelabs.kita.audio;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.common.PresetFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HrirCatalog implements AutoCloseable {
    @FunctionalInterface interface Decoder { float[][] decode(Path path, int sampleRate) throws Exception; }
    private static final int CACHE_SIZE = 16;
    private final List<PresetFiles.Preset> presets;
    private final Decoder decoder;
    private final Cache<String, ConvolutionKernel> kernels = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build();
    private final ExecutorService warmup = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "kita-hrir-warmup");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public HrirCatalog(@Value("${kita.hrir.directory:/opt/kita/presets}") String directory) throws Exception {
        this(PresetFiles.scan(Path.of(directory)), HrirDecoder::decode);
    }

    HrirCatalog(List<PresetFiles.Preset> presets, Decoder decoder) {
        this.presets = List.copyOf(presets);
        this.decoder = decoder;
        // Discord audio uses 48 kHz. Keep warmup bounded to the cache's capacity.
        warmup.execute(() -> {
            for (var preset : presets.stream().limit(CACHE_SIZE).toList()) {
                if (Thread.currentThread().isInterrupted()) return;
                try { kernel(preset.id(), 48_000); }
                catch (IllegalArgumentException error) {
                    org.slf4j.LoggerFactory.getLogger(HrirCatalog.class).warn("Could not warm HRIR preset {}", preset.id(), error);
                }
            }
        });
    }
    public List<PresetFiles.Preset> presets() { return presets; }
    public ConvolutionKernel kernel(String id, int sampleRate) {
        return kernels.get(id + ":" + sampleRate, ignored -> {
            var preset = presets.stream().filter(p -> p.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown HRIR preset"));
            try { return new ConvolutionKernel(decoder.decode(preset.path(), sampleRate)); }
            catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalArgumentException("Cannot decode HRIR preset", error);
            }
        });
    }
    @Override public void close() { warmup.shutdownNow(); }
}
