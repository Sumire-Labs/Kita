package com.sumirelabs.kita.previews;

import java.util.List;

public record Preview(String platform, String author, String text, String url,
                      List<String> media, String context, boolean sensitive, boolean replacesOriginal, Engagement engagement) {
    public Preview {
        media = List.copyOf(media);
        engagement = java.util.Objects.requireNonNullElseGet(engagement, Engagement::empty);
    }
    public Preview(String platform, String author, String text, String url,
                   List<String> media, String context, boolean sensitive, boolean replacesOriginal) {
        this(platform, author, text, url, media, context, sensitive, replacesOriginal, Engagement.empty());
    }
    public Preview(String platform, String author, String text, String url,
                   List<String> media, String context, boolean sensitive) {
        this(platform, author, text, url, media, context, sensitive, true);
    }
    public Preview withEngagement(Engagement counts) {
        return new Preview(platform, author, text, url, media, context, sensitive, replacesOriginal, counts);
    }
}
