package com.sumirelabs.kita.earthquake;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class P2pNoticeParserTest {
    private final P2pNoticeParser parser = new P2pNoticeParser();
    @Test void parsesObservedIntensityTimesCorrectionsAndUnknownHypocenters() throws Exception {
        var notice = parser.parse(Notices.payload()).orElseThrow();
        assertEquals(EarthquakeNotice.Kind.EARTHQUAKE, notice.kind());
        assertEquals(40, notice.maxScale()); assertEquals(Instant.parse("2026-10-12T01:00:00Z"), notice.issuedAt());
        assertEquals(2, notice.areas().size()); assertEquals(5.0, notice.magnitude()); assertEquals(10, notice.depth());
        assertTrue(notice.notes().getFirst().contains("津波の心配"));
        var unknown = parser.parse(Notices.payload().replace("\"magnitude\":5.0", "\"magnitude\":-1").replace("\"depth\":10", "\"depth\":-1")
                .replace("\"correct\":\"None\"", "\"correct\":\"ScaleOnly\"")).orElseThrow();
        assertNull(unknown.magnitude()); assertNull(unknown.depth()); assertTrue(unknown.title().contains("訂正"));
    }
    @Test void warningUsesEventAndSerialButNeverDisplaysForecastOnlyMagnitudeOrDepth() throws Exception {
        String payload = """
                {"code":556,"issue":{"time":"2026/10/12 10:00:00","eventId":"20261012100000","serial":"2"},
                "earthquake":{"condition":"仮定震源要素","originTime":"2026/10/12 09:59:00","hypocenter":{"name":"仮の震源","depth":10,"magnitude":1}},
                "areas":[{"pref":"東京都","name":"東京地方","scaleFrom":45,"scaleTo":99,"kindCode":"19"}]}
                """;
        var notice = parser.parse(payload).orElseThrow();
        assertEquals(2, notice.revision()); assertEquals(-1, notice.maxScale()); assertEquals("", notice.place());
        assertNull(notice.magnitude()); assertNull(notice.depth()); assertTrue(notice.notes().getFirst().contains("仮定値"));
        var cancelled = parser.parse(payload.replace("\"code\":556", "\"code\":556,\"cancelled\":true")).orElseThrow();
        assertTrue(cancelled.cancelled()); assertTrue(cancelled.supersedes(notice));
        assertFalse(notice.supersedes(cancelled));
        assertTrue(parser.parse(payload.replace("\"code\":556", "\"code\":556,\"test\":true")).isEmpty());
    }
    @Test void tsunamiKeepsWarningGradesHeightsArrivalsAndRelease() throws Exception {
        var notice = parser.parse("""
                {"code":552,"issue":{"time":"2026/10/12 10:00:00"},"areas":[{"name":"千葉県九十九里・外房","grade":"MajorWarning",
                "immediate":true,"maxHeight":{"description":"巨大"},"firstHeight":{"condition":"津波到達中と推測"}}]}
                """).orElseThrow();
        assertEquals(EarthquakeNotice.Kind.TSUNAMI, notice.kind());
        assertTrue(notice.areas().getFirst().detail().contains("大津波警報 · 巨大"));
        var released = parser.parse("{\"code\":552,\"cancelled\":true,\"issue\":{\"time\":\"2026/10/12 10:01:00\"}}").orElseThrow();
        assertTrue(released.cancelled()); assertTrue(released.supersedes(notice));
    }
    @Test void ignoresUnsupportedOrUntimedPacketsAndKeepsUnknownScaleUnknown() throws Exception {
        assertTrue(parser.parse("{\"code\":555}").isEmpty());
        assertTrue(parser.parse("{\"code\":551,\"issue\":{\"time\":\"bad\"}}").isEmpty());
        assertEquals("不明", SeismicIntensity.label(99)); assertEquals("6弱", SeismicIntensity.label(55));
    }
}