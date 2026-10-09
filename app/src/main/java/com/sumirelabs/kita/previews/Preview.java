package com.sumirelabs.kita.previews;

import java.util.List;

public record Preview(String platform, String author, String text, String url,
                      List<String> media, String context, boolean sensitive) {
    public Preview { media = List.copyOf(media); }
}
