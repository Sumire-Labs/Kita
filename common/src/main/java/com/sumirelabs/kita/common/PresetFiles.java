package com.sumirelabs.kita.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PresetFiles {
    private PresetFiles() {}
    public record Preset(String id, String name, Path path) {}

    public static List<Preset> scan(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) return List.of();
        var root = directory.toRealPath();
        Map<String, Preset> presets = new TreeMap<>();
        try (var paths = Files.walk(root, 8)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                if (!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".wav")) continue;
                var real = path.toRealPath();
                if (!real.startsWith(root)) continue;
                var name = root.relativize(path).toString().replace('\\', '/');
                var digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
                var id = HexFormat.of().formatHex(digest, 0, 8);
                if (presets.put(id, new Preset(id, name, real)) != null) throw new IllegalStateException("Preset ID collision");
            }
        }
        return presets.values().stream().sorted(java.util.Comparator.comparing(Preset::name)).toList();
    }
}
