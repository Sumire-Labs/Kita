package com.sumirelabs.kita.levels;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.ParagraphBuilder;
import io.github.humbleui.skija.paragraph.ParagraphStyle;
import io.github.humbleui.skija.paragraph.TextStyle;

final class CardText {
    private final FontCollection fonts;
    CardText(FontCollection fonts) { this.fonts = fonts; }
    void draw(Canvas canvas, String text, float x, float y, float width, float size, int color, boolean bold) {
        try (var style = new TextStyle().setFontFamily("ProfileFont").setFontSize(size).setColor(color)
                .setFontStyle(bold ? FontStyle.BOLD : FontStyle.NORMAL);
             var paragraphStyle = new ParagraphStyle().setTextStyle(style).setMaxLinesCount(1).setEllipsis("…");
             var builder = new ParagraphBuilder(paragraphStyle, fonts);
             var paragraph = builder.addText(text.replaceAll("[\\r\\n\\p{Cntrl}]", " ")).build()) {
            paragraph.layout(width).paint(canvas, x, y);
        }
    }
}
