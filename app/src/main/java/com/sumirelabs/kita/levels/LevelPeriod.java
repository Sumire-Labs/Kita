package com.sumirelabs.kita.levels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;

public enum LevelPeriod {
    TOTAL("累計"), WEEK("週間"), MONTH("月間");
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private final String label;
    LevelPeriod(String label) { this.label = label; }
    public String label() { return label; }
    public static LevelPeriod parse(String text) {
        return switch (text.toUpperCase(java.util.Locale.ROOT)) {
            case "TOTAL", "ALL", "累計" -> TOTAL;
            case "WEEK", "WEEKLY", "週間" -> WEEK;
            case "MONTH", "MONTHLY", "月間" -> MONTH;
            default -> throw new IllegalArgumentException("期間は累計・週間・月間から選択してください。");
        };
    }
    public LocalDate since(Instant now) {
        LocalDate date = now.atZone(ZONE).toLocalDate();
        return switch (this) {
            case TOTAL -> null;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }
}
