package com.sumirelabs.kita.levels;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

public final class ProfileCardRenderer {
    public record Card(long userId, String name, long xp, long rank) {}
    public static final int WIDTH = 1240, HEIGHT = 460;
    private static final class FontBytes {
        static final byte[] DATA = load();
        static byte[] load() {
            try (var stream = ProfileCardRenderer.class.getResourceAsStream("/fonts/NotoSansJP.ttf")) {
                if (stream == null) throw new IllegalStateException("Profile font missing");
                return stream.readAllBytes();
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }
    public byte[] render(Card card, byte[] avatar) {
        try (var surface = Surface.makeRaster(ImageInfo.makeN32Premul(WIDTH, HEIGHT));
             var fontData = Data.makeFromBytes(FontBytes.DATA);
             var sourceFace = FontMgr.getDefault().makeFromData(fontData);
             var typeface = sourceFace.makeClone(new FontVariation("wght", 450));
             var boldFace = sourceFace.makeClone(new FontVariation("wght", 700));
             var provider = new TypefaceFontProvider().registerTypeface(typeface, "ProfileFont").registerTypeface(boldFace, "ProfileFont");
             var fonts = new FontCollection().setAssetFontManager(provider).setDefaultFontManager(FontMgr.getDefault());
             var paint = new Paint().setAntiAlias(true)) {
            var canvas = surface.getCanvas();
            var theme = ProfilePattern.draw(canvas, card.userId()); int accent = theme.accent();
            canvas.drawRRect(RRect.makeXYWH(24, 24, WIDTH - 48, HEIGHT - 48, 32), paint.setColor(0xA60E1728));
            var text = new CardText(fonts);
            text.draw(canvas, "KITA  /  PROFILE", 60, 50, 800, 20, 0xFF94A3BA, false);
            avatar(canvas, avatar, accent, paint);
            text.draw(canvas, card.name(), 320, 98, 850, 44, 0xFFF1F5FC, true);
            text.draw(canvas, "Lv." + LevelCurve.level(card.xp()), 320, 178, 410, 62, accent, true);
            text.draw(canvas, "サーバー順位  #" + card.rank(), 785, 205, 400, 28, 0xFFD4DEED, false);
            canvas.drawRRect(RRect.makeXYWH(320, 294, 850, 22, 11), paint.setColor(0xFF26364F));
            float width = (float) (850 * Math.clamp(LevelCurve.progress(card.xp()), 0, 1));
            if (width > 0) {
                try (var gradient = Shader.makeLinearGradient(320, 294, 1170, 316, new int[]{accent, theme.secondary()})) {
                    canvas.drawRRect(RRect.makeXYWH(320, 294, width, 22, 11), paint.setShader(gradient));
                }
                paint.setShader(null);
            }
            text.draw(canvas, card.xp() >= LevelCurve.MAX_XP ? "MAX LEVEL" : "次のレベルまで " + LevelCurve.remaining(card.xp()) + " XP",
                    320, 338, 600, 27, 0xFFD4DEED, false);
            text.draw(canvas, String.format(java.util.Locale.ROOT, "%,d XP", card.xp()), 910, 342, 270, 23, 0xFF94A3BA, false);
            try (var image = surface.makeImageSnapshot(); var png = EncoderPNG.encode(image)) {
                if (png == null) throw new IllegalStateException("Profile PNG encoding failed");
                return png.getBytes();
            }
        }
    }
    private static void avatar(Canvas canvas, byte[] bytes, int accent, Paint paint) {
        canvas.drawCircle(174, 222, 110, paint.setColor(accent));
        if (bytes.length == 0) { canvas.drawCircle(174, 222, 102, paint.setColor(0xFF25364D)); return; }
        try (var image = Image.makeDeferredFromEncodedBytes(bytes)) {
            canvas.save(); canvas.clipRRect(RRect.makeXYWH(72, 120, 204, 204, 102), true);
            canvas.drawImageRect(image, Rect.makeXYWH(72, 120, 204, 204)); canvas.restore();
        } catch (RuntimeException error) {
            canvas.restoreToCount(1);
            canvas.drawCircle(174, 222, 102, paint.setColor(0xFF25364D));
        }
    }
}
