package com.sumirelabs.kita.logshare;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LogDetector {
    private static final Pattern TIME = Pattern.compile("(?m)^\\[?(?:\\d{4}[-/]\\d{2}[-/]\\d{2}[ T])?\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern LEVEL = Pattern.compile("\\b(INFO|WARN|ERROR|DEBUG|FATAL|SEVERE)\\b");
    private static final Pattern GAME = Pattern.compile("starting minecraft server|net\\.minecraft\\.|fabric loader|"
            + "minecraftforge|neoforged|this server is running paper|paper version|com\\.hypixel\\.hytale\\.|"
            + "hytale server|\\bhytale(?:server|client)\\b|\\[hytale\\]", Pattern.CASE_INSENSITIVE);
    private LogDetector() {}
    public static boolean supported(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".log") || name.endsWith(".txt");
    }
    public static boolean confident(String text) {
        if (text.lines().limit(3).count() < 3) return false;
        if (text.contains("---- Minecraft Crash Report ----")) return true;
        return TIME.matcher(text).results().limit(2).count() == 2
                && LEVEL.matcher(text).find() && GAME.matcher(text).find();
    }
    static String unwrap(String text) {
        String stripped = text.strip();
        if (stripped.startsWith("```") && stripped.endsWith("```")) {
            int newline = stripped.indexOf('\n');
            if (newline >= 0) return stripped.substring(newline + 1, stripped.length() - 3).strip();
        }
        return text;
    }
}
