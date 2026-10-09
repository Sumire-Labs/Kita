package com.sumirelabs.kita.audio;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HrirCatalogTest {
    @Test void warmupRunsInBackgroundAndSharesTheCachedKernelWithPlayback() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var catalog = new HrirCatalog(List.of(new Preset("test", "Test", Path.of("test.wav"))), (path, rate) -> {
            assertEquals(48_000, rate);
            calls.incrementAndGet();
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Decoder stalled");
            return new float[4][1];
        })) {
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                // The constructor and metadata remain available while decoding is in progress.
                assertEquals(1, catalog.presets().size());
                try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
                    var playback = tasks.submit(() -> catalog.kernel("test", 48_000));
                    release.countDown();
                    var kernel = playback.get(5, TimeUnit.SECONDS);
                    assertSame(kernel, catalog.kernel("test", 48_000));
                    assertEquals(1, calls.get());
                }
            } finally { release.countDown(); }
        }
    }

    @Test void invalidPresetDoesNotPreventWarmingTheNextPreset() throws Exception {
        var decoded = new CountDownLatch(1);
        var presets = List.of(new Preset("bad", "Bad", Path.of("bad.wav")), new Preset("good", "Good", Path.of("good.wav")));
        try (var catalog = new HrirCatalog(presets, (path, rate) -> {
            if (path.equals(Path.of("bad.wav"))) throw new IllegalArgumentException("Invalid fixture");
            decoded.countDown();
            return new float[4][1];
        })) {
            assertTrue(decoded.await(5, TimeUnit.SECONDS));
            assertNotNull(catalog.kernel("good", 48_000));
        }
    }
}
