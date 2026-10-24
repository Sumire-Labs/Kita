package com.sumirelabs.kita.earthquake;

import java.time.Instant;
import java.util.List;

public final class Notices {
    private Notices() {}
    public static EarthquakeNotice warning(long revision, boolean cancelled) {
        long time = Instant.parse("2026-10-12T01:00:00Z").toEpochMilli();
        return new EarthquakeNotice("P2P地震情報", "https://www.p2pquake.net/", "p2pquake:eew:20261012100000",
                EarthquakeNotice.Kind.EEW_WARNING, revision, time + revision * 1000, time, cancelled, false, -1,
                cancelled ? "緊急地震速報（警報）取消" : "緊急地震速報（警報）", "テスト用の震央", null, null,
                List.of(), cancelled ? List.of() : List.of(new EarthquakeNotice.Area("東京都", "東京地方", -1, "警報対象地域")));
    }
    public static String payload() {
        return """
                {"id":"test-id","code":551,"issue":{"time":"2026/10/12 10:00:00","type":"ScaleAndDestination","correct":"None"},
                "earthquake":{"time":"2026/10/12 09:59:00","hypocenter":{"name":"テスト","magnitude":5.0,"depth":10},"maxScale":40,"domesticTsunami":"None"},
                "points":[{"pref":"東京都","addr":"東京","scale":40},{"pref":"千葉県","addr":"千葉","scale":20}],"comments":{}}
                """;
    }
}