package com.sumirelabs.kita.levels;

public final class LevelCurve {
    public static final int MAX_LEVEL = 100_000;
    public static final long MAX_XP = 100L * MAX_LEVEL * (MAX_LEVEL + 1L);
    private LevelCurve() {}
    public static long threshold(int level) { return 100L * level * (level + 1L); }
    public static int level(long xp) {
        int low = 0, high = MAX_LEVEL;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (threshold(middle) <= xp) low = middle; else high = middle - 1;
        }
        return low;
    }
    public static long remaining(long xp) { return xp >= MAX_XP ? 0 : threshold(level(xp) + 1) - xp; }
    public static double progress(long xp) {
        if (xp >= MAX_XP) return 1;
        int level = level(xp);
        return (double) (xp - threshold(level)) / (threshold(level + 1) - threshold(level));
    }
}
