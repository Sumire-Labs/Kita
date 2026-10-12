package com.sumirelabs.kita.earthquake;

import java.util.List;

public final class SeismicIntensity {
    public static final List<Integer> SCALES = List.of(0, 10, 20, 30, 40, 45, 50, 55, 60, 70);
    private SeismicIntensity() {}
    public static int normalize(int scale) { return SCALES.contains(scale) ? scale : -1; }
    public static String label(int scale) {
        return switch (scale) {
            case 0 -> "0"; case 10 -> "1"; case 20 -> "2"; case 30 -> "3"; case 40 -> "4";
            case 45 -> "5弱"; case 50 -> "5強"; case 55 -> "6弱"; case 60 -> "6強"; case 70 -> "7";
            default -> "不明";
        };
    }
}
