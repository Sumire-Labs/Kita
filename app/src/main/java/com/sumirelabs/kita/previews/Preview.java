package com.sumirelabs.kita.previews;

import java.util.List;

public record Preview(String platform, String author, String text, String url,
                      List<String> media, String context, boolean sensitive, boolean replacesOriginal) {
    public Preview { media = List.copyOf(media); }
    public Preview(String platform, String author, String text, String url,
                   List<String> media, String context, boolean sensitive) {
        this(platform, author, text, url, media, context, sensitive, true);
    }
}
