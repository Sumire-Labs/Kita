package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record EarthquakeConfig(boolean enabled, Set<String> channels, Set<String> kinds, int minimum,
                               List<String> regions, List<String> tsunamiRegions, boolean silent, long role, String updates) {
    public static EarthquakeConfig from(GuildSettings settings) {
        int minimum;
        try { minimum = Integer.parseInt(settings.value("earthquake.minimum", "0")); } catch (NumberFormatException error) { minimum = 0; }
        return new EarthquakeConfig(settings.enabled("earthquake.enabled"), ids(settings.value("earthquake.channels", "")),
                Set.copyOf(Arrays.asList(settings.value("earthquake.kinds", "EARTHQUAKE,EEW_WARNING,TSUNAMI").split(","))),
                SeismicIntensity.SCALES.contains(minimum) ? minimum : 0, keywords(settings.value("earthquake.regions", "")),
                keywords(settings.value("earthquake.tsunamiRegions", "")), settings.value("earthquake.sound", "normal").equals("silent"),
                settings.snowflake("earthquake.role"), settings.value("earthquake.updates", "new"));
    }
    public boolean matches(EarthquakeNotice notice) {
        if (!enabled || !kinds.contains(notice.kind().name()) || notice.test()) return false;
        if (notice.cancelled()) return false; // Cancellation is routed only to channels which received the original event.
        var targets = notice.kind() == EarthquakeNotice.Kind.TSUNAMI ? tsunamiRegions : regions;
        boolean region = targets.isEmpty() || targets.stream().anyMatch(keyword -> notice.areas().stream()
                .anyMatch(area -> area.prefecture().contains(keyword) || area.name().contains(keyword)));
        if (!region) return false;
        if (notice.kind() != EarthquakeNotice.Kind.EARTHQUAKE || minimum == 0) return true;
        int intensity = targets.isEmpty() ? notice.maxScale() : notice.areas().stream()
                .filter(area -> targets.stream().anyMatch(key -> area.prefecture().contains(key) || area.name().contains(key)))
                .mapToInt(EarthquakeNotice.Area::scale).max().orElse(-1);
        return intensity >= minimum;
    }
    static Set<String> ids(String value) {
        return Arrays.stream(value.split(",")).filter(id -> id.matches("[0-9]{1,20}")).collect(Collectors.toUnmodifiableSet());
    }
    public static List<String> keywords(String value) {
        return Arrays.stream(value.split("[,、\\n]")).map(String::strip).filter(text -> !text.isEmpty()).distinct().toList();
    }
}
