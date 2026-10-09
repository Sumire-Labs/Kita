package com.sumirelabs.kita.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PresetMetadata {
    record Entry(String name, String description) {}
    private PresetMetadata() {}

    static Entry lookup(Path root, Path wav, Map<Path, Map<String, Entry>> cache) {
        String filename = wav.getFileName().toString();
        String key = filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT);
        for (var directory = wav.getParent(); directory != null && directory.startsWith(root); directory = directory.getParent()) {
            var entry = cache.computeIfAbsent(directory, PresetMetadata::read).get(key);
            if (entry != null) return entry;
        }
        return null;
    }

    private static Map<String, Entry> read(Path directory) {
        var file = directory.resolve("info.csv");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        var entries = new HashMap<String, Entry>();
        try {
            if (Files.size(file) > 1_048_576) return Map.of();
            // HeSuVi uses an unquoted two-column format; quotes inside descriptions are literal.
            for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                int separator = line.indexOf(';');
                if (separator < 1) continue;
                String key = line.substring(0, separator).strip().toLowerCase(Locale.ROOT);
                String description = line.substring(separator + 1).strip().replace("/n", "\n").replace("\\n", "\n");
                if (key.equals("*") || key.isBlank() || description.isBlank()) continue;
                if (key.endsWith(".wav")) key = key.substring(0, key.length() - 4);
                entries.put(key, new Entry(displayName(description), description));
            }
        } catch (IOException ignored) { return Map.of(); }
        return Map.copyOf(entries);
    }

    private static String displayName(String description) {
        String name = description.lines().findFirst().orElse(description);
        name = name.split("(?<=[!.])\\s", 2)[0];
        name = name.replace(" 7.1 virtual surround sound for headphones", "")
                .replaceAll(" \\(also known as [^)]+\\)", "")
                .replaceAll(" (?:recorded by|by) .*$", "")
                .replace(" without reverb", " (No reverb)").strip();
        return name.isBlank() ? description : name;
    }
}
