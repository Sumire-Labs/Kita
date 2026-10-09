package com.sumirelabs.kita.levels;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Point;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;

final class ProfilePattern {
    record Theme(int accent, int secondary) {}
    private ProfilePattern() {}
    static long seed(long user) {
        try { return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(("kita-profile-v2:" + user).getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static Theme draw(Canvas canvas, long user) {
        long seed = seed(user); var random = new SplittableRandom(seed);
        float hue = (float) random.nextDouble();
        float second = (hue + 0.10f + (float) random.nextDouble(0.16)) % 1;
        var theme = new Theme(color(hue, 0.48f, 1), color(second, 0.55f, 1));
        try (var paint = new Paint().setAntiAlias(true);
             var background = Shader.makeLinearGradient(0, 0, 1240, 460,
                     new int[]{color(hue, 0.48f, 0.10f), color(second, 0.65f, 0.24f)})) {
            canvas.drawPaint(paint.setShader(background)); paint.setShader(null);
            int family = Math.floorMod(seed, 4);
            if (family == 0) triangles(canvas, random, hue, paint);
            else if (family == 1) rings(canvas, random, hue, paint);
            else if (family == 2) bands(canvas, random, hue, paint);
            else diamonds(canvas, random, hue, paint);
        }
        return theme;
    }
    private static void triangles(Canvas canvas, SplittableRandom random, float hue, Paint paint) {
        var grid = new Point[7][4];
        for (int x = 0; x < 7; x++) for (int y = 0; y < 4; y++) {
            grid[x][y] = new Point(x * 300 - 200 + (float) random.nextDouble(-70, 70),
                    y * 300 - 200 + (float) random.nextDouble(-70, 70));
        }
        for (int x = 0; x < 6; x++) for (int y = 0; y < 3; y++) {
            canvas.drawTriangles(new Point[]{grid[x][y], grid[x + 1][y], grid[x][y + 1]}, null, paint.setColor(tint(random, hue, 155)));
            canvas.drawTriangles(new Point[]{grid[x + 1][y], grid[x + 1][y + 1], grid[x][y + 1]}, null, paint.setColor(tint(random, hue, 155)));
        }
    }
    private static void rings(Canvas canvas, SplittableRandom random, float hue, Paint paint) {
        for (int i = 0; i < 8; i++) {
            float radius = (float) random.nextDouble(110, 290);
            paint.setMode(i % 3 == 0 ? PaintMode.FILL : PaintMode.STROKE).setStrokeWidth((float) random.nextDouble(35, 80));
            canvas.drawCircle((float) random.nextDouble(-100, 1340), (float) random.nextDouble(-100, 560), radius,
                    paint.setColor(tint(random, hue, 150)));
        }
        paint.setMode(PaintMode.FILL);
    }
    private static void bands(Canvas canvas, SplittableRandom random, float hue, Paint paint) {
        float angle = (float) random.nextDouble(-35, 35);
        for (int i = 0; i < 7; i++) {
            canvas.save(); canvas.translate(620, -270 + i * 150); canvas.rotate(angle);
            canvas.drawRRect(RRect.makeXYWH(-1000, -40, 2000, (float) random.nextDouble(70, 145), 25),
                    paint.setColor(tint(random, hue, 150))); canvas.restore();
        }
    }
    private static void diamonds(Canvas canvas, SplittableRandom random, float hue, Paint paint) {
        for (int i = 0; i < 9; i++) {
            float size = (float) random.nextDouble(160, 400);
            canvas.save(); canvas.translate((float) random.nextDouble(-50, 1290), (float) random.nextDouble(-100, 560));
            canvas.rotate(45);
            canvas.drawRRect(RRect.makeXYWH(-size / 2, -size / 2, size, size, 18), paint.setColor(tint(random, hue, 150)));
            canvas.restore();
        }
    }
    private static int tint(SplittableRandom random, float hue, int alpha) {
        float h = (float) ((hue + random.nextDouble(-0.14, 0.14) + 1) % 1);
        return (color(h, (float) random.nextDouble(0.45, 0.8), (float) random.nextDouble(0.4, 0.95)) & 0xFFFFFF) | alpha << 24;
    }
    private static int color(float hue, float saturation, float brightness) { return Color.HSBtoRGB(hue, saturation, brightness); }
}
