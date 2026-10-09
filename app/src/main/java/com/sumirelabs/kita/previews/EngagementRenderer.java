package com.sumirelabs.kita.previews;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static com.sumirelabs.kita.previews.Engagement.Metric.*;

public final class EngagementRenderer {
    private EngagementRenderer() {}

    public static String render(Preview preview) {
        var platform = preview.platform().toLowerCase(Locale.ROOT);
        var metrics = new ArrayList<>(switch (platform) {
            case "x", "twitter" -> List.of(REPLIES, REPOSTS, LIKES, VIEWS);
            case "instagram" -> List.of(LIKES, COMMENTS);
            case "tiktok" -> List.of(LIKES, COMMENTS, SHARES, SAVES, VIEWS);
            case "reddit" -> List.of(SCORE, COMMENTS);
            case "youtube" -> List.of(LIKES, COMMENTS, VIEWS);
            case "twitch" -> List.of(VIEWS);
            default -> List.<Engagement.Metric>of();
        });
        for (var metric : Engagement.Metric.values()) {
            if (preview.engagement().values().containsKey(metric) && !metrics.contains(metric)) metrics.add(metric);
        }
        var format = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        var parts = new ArrayList<String>();
        for (var metric : metrics) {
            var value = preview.engagement().values().get(metric);
            parts.add(label(metric, platform) + " **" + (value == null ? "—" : format.format(value)) + "**");
        }
        return String.join(" · ", parts);
    }

    private static String label(Engagement.Metric metric, String platform) {
        return switch (metric) {
            case LIKES -> platform.equals("youtube") ? "👍 高評価" : "❤ いいね";
            case REPOSTS -> "🔁 リポスト";
            case REPLIES -> "💬 返信";
            case COMMENTS -> "💬 コメント";
            case VIEWS -> platform.equals("x") || platform.equals("twitter") ? "👁 表示" : "▶ 再生";
            case SAVES -> platform.equals("x") || platform.equals("twitter") ? "🔖 ブックマーク" : "🔖 保存";
            case QUOTES -> "❞ 引用";
            case SCORE -> "⬆ スコア";
            case CROSSPOSTS -> "🔁 クロスポスト";
            case SHARES -> "↗ シェア";
        };
    }
}
