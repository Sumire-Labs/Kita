package com.sumirelabs.kita.previews;

import java.util.Map;

public record Engagement(Map<Metric, Long> values) {
    public enum Metric { LIKES, REPOSTS, REPLIES, COMMENTS, VIEWS, SAVES, QUOTES, SCORE, CROSSPOSTS, SHARES }
    public Engagement { values = Map.copyOf(values); }
    public static Engagement empty() { return new Engagement(Map.of()); }
}
