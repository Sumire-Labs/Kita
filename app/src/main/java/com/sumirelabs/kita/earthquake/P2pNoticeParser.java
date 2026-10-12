package com.sumirelabs.kita.earthquake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class P2pNoticeParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public Optional<EarthquakeNotice> parse(String payload) throws Exception { return parse(JSON.readTree(payload)); }
    public Optional<EarthquakeNotice> parse(JsonNode data) {
        int code = data.path("code").asInt();
        if (code != 551 && code != 552 && code != 556) return Optional.empty();
        var issue = data.path("issue"); var earthquake = data.path("earthquake"); var hypocenter = earthquake.path("hypocenter");
        long issued = time(issue.path("time").asText());
        if (issued == 0 || data.path("test").asBoolean(false)) return Optional.empty();
        String id = data.path("id").asText("");
        var areas = new ArrayList<EarthquakeNotice.Area>(); var notes = new ArrayList<String>();
        boolean cancelled = data.path("cancelled").asBoolean(false);
        EarthquakeNotice.Kind kind; String key, title; long revision = issued;
        Double magnitude = null; Integer depth = null; int scale = -1;
        if (code == 556) {
            kind = EarthquakeNotice.Kind.EEW_WARNING; String event = issue.path("eventId").asText();
            if (!event.matches("[0-9]{8,32}")) return Optional.empty();
            key = "p2pquake:eew:" + event; revision = issue.path("serial").asLong(-1);
            if (revision < 0) return Optional.empty();
            title = cancelled ? "緊急地震速報（警報）取消" : "緊急地震速報（警報）";
            for (var area : data.path("areas")) areas.add(new EarthquakeNotice.Area(area.path("pref").asText(),
                    area.path("name").asText(), -1, switch (area.path("kindCode").asText()) {
                case "10" -> "主要動は未到達と予測"; case "11" -> "主要動は既に到達と予測";
                case "19" -> "PLUM法による警報"; default -> "警報対象地域";
            }));
            if (cancelled) notes.add("この緊急地震速報は取り消されました。");
            if (earthquake.path("condition").asText().equals("仮定震源要素")) notes.add("震源要素は仮定値のため表示していません。");
        } else if (code == 552) {
            kind = EarthquakeNotice.Kind.TSUNAMI; key = "p2pquake:tsunami"; title = cancelled ? "津波警報・注意報 解除" : "津波情報";
            for (var area : data.path("areas")) {
                String grade = switch (area.path("grade").asText()) {
                    case "MajorWarning" -> "大津波警報"; case "Warning" -> "津波警報"; case "Watch" -> "津波注意報"; default -> "種類不明";
                };
                String height = area.path("maxHeight").path("description").asText("");
                var first = area.path("firstHeight");
                String arrival = first.path("condition").asText("");
                if (arrival.isBlank()) arrival = first.path("arrivalTime").asText("");
                if (area.path("immediate").asBoolean(false) && arrival.isBlank()) arrival = "ただちに津波来襲と予測";
                areas.add(new EarthquakeNotice.Area("", area.path("name").asText(), -1,
                        grade + (height.isBlank() ? "" : " · " + height) + (arrival.isBlank() ? "" : " · " + arrival)));
            }
            if (!cancelled) title = areas.stream().anyMatch(area -> area.detail().startsWith("大津波警報")) ? "大津波警報"
                    : areas.stream().anyMatch(area -> area.detail().startsWith("津波警報")) ? "津波警報"
                    : areas.stream().anyMatch(area -> area.detail().startsWith("津波注意報")) ? "津波注意報" : "津波情報";
            if (cancelled) notes.add("津波警報・注意報は解除されました。");
        } else {
            kind = EarthquakeNotice.Kind.EARTHQUAKE;
            if (!id.matches("[A-Za-z0-9_-]{1,80}")) return Optional.empty();
            key = "p2pquake:quake:" + id;
            title = switch (issue.path("type").asText()) {
                case "ScalePrompt" -> "震度速報"; case "Destination" -> "震源に関する情報";
                case "Foreign" -> "遠地地震に関する情報"; default -> "震源・震度情報";
            };
            String correction = issue.path("correct").asText("None");
            if (!correction.equals("None") && !correction.equals("Unknown")) title += "（訂正）";
            scale = SeismicIntensity.normalize(earthquake.path("maxScale").asInt(-1));
            if (hypocenter.path("magnitude").isNumber() && hypocenter.path("magnitude").asDouble() >= 0) magnitude = hypocenter.path("magnitude").asDouble();
            if (hypocenter.path("depth").isNumber() && hypocenter.path("depth").asInt() >= 0) depth = hypocenter.path("depth").asInt();
            for (var point : data.path("points")) areas.add(new EarthquakeNotice.Area(point.path("pref").asText(),
                    point.path("addr").asText(), SeismicIntensity.normalize(point.path("scale").asInt(-1)), ""));
            String tsunami = switch (earthquake.path("domesticTsunami").asText()) {
                case "None" -> "この地震による津波の心配はありません。"; case "Checking" -> "津波の有無を調査中です。";
                case "NonEffective" -> "若干の海面変動が予想されますが、被害の心配はありません。";
                case "Watch" -> "津波注意報が発表されています。"; case "Warning" -> "津波警報が発表されています。"; default -> "";
            };
            if (!tsunami.isBlank()) notes.add(tsunami);
            String comment = data.path("comments").path("freeFormComment").asText("");
            if (!comment.isBlank()) notes.add(comment.substring(0, Math.min(comment.length(), 1500)));
        }
        String place = earthquake.path("condition").asText().equals("仮定震源要素") ? "" : hypocenter.path("name").asText("");
        return Optional.of(new EarthquakeNotice("P2P地震情報", "https://www.p2pquake.net/", key, kind, revision, issued,
                time(earthquake.path("time").asText(earthquake.path("originTime").asText(""))), cancelled, false, scale,
                title, place, magnitude, depth, notes, areas));
    }
    static long time(String value) {
        try {
            if (value.contains("T")) return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();
            return LocalDateTime.parse(value.length() > 19 ? value.substring(0, 19) : value, DATE).atZone(JAPAN).toInstant().toEpochMilli();
        } catch (java.time.DateTimeException error) { return 0; }
    }
}
