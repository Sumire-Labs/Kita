package com.sumirelabs.kita.previews;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import static com.sumirelabs.kita.previews.Engagement.Metric.*;

public final class EngagementParser {
    private static final Pattern VISIBLE_COUNT = Pattern.compile("(?<![\\d.])([0-9][0-9,.\\s]*)([KMB万億]?)", Pattern.CASE_INSENSITIVE);
    private EngagementParser() {}

    public static Engagement twitter(JsonNode tweet) {
        var values = new EnumMap<Engagement.Metric, Long>(Engagement.Metric.class);
        read(values, LIKES, tweet.get("likes"));
        read(values, REPOSTS, tweet.get("retweets"));
        read(values, REPLIES, tweet.get("replies"));
        read(values, VIEWS, tweet.get("views"));
        read(values, QUOTES, tweet.get("quotes"));
        read(values, SAVES, tweet.get("bookmarks"));
        return new Engagement(values);
    }

    public static Engagement video(JsonNode data, String platform) {
        var values = new EnumMap<Engagement.Metric, Long>(Engagement.Metric.class);
        read(values, LIKES, data.get("like_count"));
        read(values, COMMENTS, data.get("comment_count"));
        read(values, VIEWS, data.get("view_count"));
        read(values, SAVES, data.get("save_count"));
        // yt-dlp exposes TikTok's share_count as repost_count; those are shares, not reposts.
        read(values, platform.equalsIgnoreCase("tiktok") ? SHARES : REPOSTS, data.get("repost_count"));
        return new Engagement(values);
    }

    public static Engagement reddit(JsonNode post) {
        var values = new EnumMap<Engagement.Metric, Long>(Engagement.Metric.class);
        if (!post.path("score_hidden").asBoolean()) read(values, SCORE, post.get("score"));
        read(values, COMMENTS, post.get("num_comments"));
        read(values, CROSSPOSTS, post.get("num_crossposts"));
        return new Engagement(values);
    }

    public static Engagement instagram(Document page) {
        var values = new EnumMap<Engagement.Metric, Long>(Engagement.Metric.class);
        visible(values, LIKES, page, ".LikeCount");
        visible(values, COMMENTS, page, ".CaptionCommentsExpand, .CommentCount");
        visible(values, VIEWS, page, ".ViewCount, .PlayCount");
        return new Engagement(values);
    }

    public static Engagement redditEmbed(JsonNode metadata, Document page) {
        var values = new EnumMap<Engagement.Metric, Long>(Engagement.Metric.class);
        values.putAll(reddit(metadata).values());
        if (!metadata.path("score_hidden").asBoolean()) attribute(values, SCORE, page, "score", "data-score");
        attribute(values, COMMENTS, page, "comment-count", "data-comment-count");
        return new Engagement(values);
    }

    private static void attribute(EnumMap<Engagement.Metric, Long> values, Engagement.Metric metric,
                                  Document page, String... names) {
        for (var name : names) {
            var element = page.selectFirst("shreddit-post[" + name + "], article[" + name + "]");
            if (element != null) put(values, metric, integer(element.attr(name)));
        }
    }

    private static void visible(EnumMap<Engagement.Metric, Long> values, Engagement.Metric metric,
                                Document page, String selector) {
        var element = page.selectFirst(selector);
        if (element == null) return;
        var match = VISIBLE_COUNT.matcher(element.text());
        if (!match.find()) return;
        var number = match.group(1).strip();
        var unit = match.group(2).toUpperCase(Locale.ROOT);
        if (unit.isEmpty()) { put(values, metric, integer(number)); return; }
        long factor = switch (unit) {
            case "K" -> 1_000; case "M" -> 1_000_000; case "B" -> 1_000_000_000;
            case "万" -> 10_000; case "億" -> 100_000_000; default -> 1;
        };
        try { put(values, metric, new BigDecimal(number.replace(',', '.')).multiply(BigDecimal.valueOf(factor)).longValueExact()); }
        catch (ArithmeticException | NumberFormatException ignored) { /* Ambiguous or unavailable counter. */ }
    }

    private static void read(EnumMap<Engagement.Metric, Long> values, Engagement.Metric metric, JsonNode node) {
        if (node == null || node.isNull() || node.isBoolean()) return;
        Long number = null;
        try {
            if (node.isNumber()) number = node.decimalValue().longValueExact();
            else number = integer(node.asText());
        }
        catch (ArithmeticException ignored) { /* Fractional values and overflows are not counters. */ }
        put(values, metric, number);
    }

    private static Long integer(String text) {
        text = text.strip();
        if (!text.matches("-?(?:[0-9]+|[0-9]{1,3}(?:[, .][0-9]{3})+)")) return null;
        try { return Long.parseLong(text.replaceAll("[, .]", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static void put(EnumMap<Engagement.Metric, Long> values, Engagement.Metric metric, Long value) {
        if (value != null && (value >= 0 || metric == SCORE)) values.put(metric, value);
    }
}
