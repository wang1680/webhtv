package com.fongmi.android.tv.ui.custom;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.setting.Setting;

public class BuiltInWallDrawable extends Drawable {

    private final Paint paint;
    private final Paint bitmapPaint;
    private final int wall;
    private Bitmap bitmap;
    private int bitmapWidth;
    private int bitmapHeight;
    private int alpha = 255;
    private ColorFilter colorFilter;

    public BuiltInWallDrawable(int wall) {
        this.wall = wall;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setDither(true);
        this.bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        ensureBitmap(bounds.width(), bounds.height());
        bitmapPaint.setAlpha(alpha);
        bitmapPaint.setColorFilter(colorFilter);
        canvas.drawBitmap(bitmap, bounds.left, bounds.top, bitmapPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    private void ensureBitmap(int width, int height) {
        if (bitmap != null && bitmapWidth == width && bitmapHeight == height) return;
        if (bitmap != null) bitmap.recycle();
        bitmapWidth = width;
        bitmapHeight = height;
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        render(new Canvas(bitmap), width, height);
    }

    private void render(Canvas canvas, int width, int height) {
        switch (wall) {
            case Setting.WALL_AURORA_GLASS -> auroraFlow(canvas, width, height);
            case Setting.WALL_SUNSET_PRISM -> coralDusk(canvas, width, height);
            case Setting.WALL_MINT_GLACIER -> mintNebula(canvas, width, height);
            case Setting.WALL_LIQUID_CHROME -> silverCurrent(canvas, width, height);
            case Setting.WALL_NEON_BERRY -> berryNebula(canvas, width, height);
            case Setting.WALL_CHAMPAGNE_MIST -> champagneDawn(canvas, width, height);
            case Setting.WALL_GLASS_GRADIENT -> glassGradient(canvas, width, height);
            case Setting.WALL_DEEP_SPACE_GLASS -> deepSpaceGlass(canvas, width, height);
            case Setting.WALL_POLAR_LIGHT_GLASS -> polarLightGlass(canvas, width, height);
            case Setting.WALL_NEON_CYBER -> neonCyber(canvas, width, height);
            case Setting.WALL_WARM_MOON_GLASS -> warmMoonGlass(canvas, width, height);
            case Setting.WALL_CRYSTAL_SKY -> crystalSky(canvas, width, height);
            case Setting.WALL_DREAM_PURPLE -> dreamPurple(canvas, width, height);
            case Setting.WALL_SKY_MINT -> skyMint(canvas, width, height);
            case Setting.WALL_FOREST_MIST -> forestMist(canvas, width, height);
            case Setting.WALL_DAYLIGHT_MINIMAL -> daylightMinimal(canvas, width, height);
            case Setting.WALL_DEEP_SEA -> deepSea(canvas, width, height);
            case Setting.WALL_VIOLET_SMOKE -> violetSmoke(canvas, width, height);
            case Setting.WALL_ROSE_VEIL -> roseVeil(canvas, width, height);
            case Setting.WALL_EMERALD_AURORA -> emeraldAurora(canvas, width, height);
            case Setting.WALL_BLUE_SILK -> blueSilk(canvas, width, height);
            case Setting.WALL_PEACH_DAWN -> peachDawn(canvas, width, height);
            case Setting.WALL_GRAPHITE_SMOKE -> graphiteSmoke(canvas, width, height);
            case Setting.WALL_PASTEL_PRISM -> pastelPrism(canvas, width, height);
            case Setting.WALL_MIDNIGHT_MOON -> midnightMoon(canvas, width, height);
            case Setting.WALL_CYAN_CRYSTAL -> cyanCrystal(canvas, width, height);
            case Setting.WALL_LAVENDER_CRYSTAL -> lavenderCrystal(canvas, width, height);
            default -> solid(canvas, width, height);
        }
        if (isLightWall()) {
            lightVignette(canvas, width, height);
            lightReadability(canvas, width, height);
        } else {
            vignette(canvas, width, height);
        }
        grain(canvas, width, height);
    }

    private void auroraFlow(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF0B2737, 0xFF2B8ECB, 0xFF8B6FE8);
        glow(canvas, width * 0.12f, height * 0.20f, width * 0.80f, 0xAA46D6FF);
        glow(canvas, width * 0.86f, height * 0.18f, width * 0.76f, 0x8A5B79FF);
        glow(canvas, width * 0.54f, height * 0.86f, width * 0.82f, 0x884ED9B7);
        flow(canvas, width, height, 0x66CFFBFF, 0.16f, 0.40f, 0.54f, 0.08f);
        flow(canvas, width, height, 0x4D9E7DFF, 0.46f, 0.68f, 0.42f, -0.16f);
        haze(canvas, width, height, 0x33FFFFFF, 0.18f, 0.58f, 1.06f);
    }

    private void coralDusk(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF24152F, 0xFFFF6C8E, 0xFF5B67D6);
        glow(canvas, width * 0.18f, height * 0.22f, width * 0.78f, 0xB5FF8E72);
        glow(canvas, width * 0.82f, height * 0.52f, width * 0.76f, 0x96FFD466);
        glow(canvas, width * 0.60f, height * 0.12f, width * 0.58f, 0x805C7CFA);
        flow(canvas, width, height, 0x70FFE7B1, 0.30f, 0.56f, 0.50f, -0.12f);
        flow(canvas, width, height, 0x4DFF72B9, 0.58f, 0.82f, 0.42f, 0.20f);
        haze(canvas, width, height, 0x28FFFFFF, 0.08f, 0.40f, 0.96f);
    }

    private void mintNebula(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF12323D, 0xFF55D8BE, 0xFF6C82F1);
        glow(canvas, width * 0.76f, height * 0.18f, width * 0.76f, 0xAA90FFE5);
        glow(canvas, width * 0.18f, height * 0.72f, width * 0.84f, 0x7747B8FF);
        glow(canvas, width * 0.46f, height * 0.48f, width * 0.54f, 0x66E7FFF6);
        flow(canvas, width, height, 0x6695FFF1, 0.16f, 0.32f, 0.34f, 0.18f);
        flow(canvas, width, height, 0x446BA8FF, 0.54f, 0.82f, 0.48f, -0.08f);
        haze(canvas, width, height, 0x2CFFFFFF, 0.24f, 0.58f, 0.88f);
    }

    private void silverCurrent(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF121826, 0xFF53657F, 0xFF27304C);
        glow(canvas, width * 0.28f, height * 0.18f, width * 0.74f, 0x7CA8D4FF);
        glow(canvas, width * 0.86f, height * 0.68f, width * 0.90f, 0x88DDB3FF);
        glow(canvas, width * 0.34f, height * 0.82f, width * 0.76f, 0x55FFF7EC);
        current(canvas, width, height, 0x68FFFFFF, 0.22f, -0.18f);
        current(canvas, width, height, 0x4EA7D7FF, 0.50f, 0.08f);
        current(canvas, width, height, 0x3DF2E9FF, 0.72f, -0.04f);
        haze(canvas, width, height, 0x22FFFFFF, 0.02f, 0.36f, 1.00f);
    }

    private void berryNebula(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF201337, 0xFF7B42CF, 0xFF0F4660);
        glow(canvas, width * 0.18f, height * 0.20f, width * 0.76f, 0xB0FF4FCB);
        glow(canvas, width * 0.82f, height * 0.28f, width * 0.68f, 0x8A6BDBFF);
        glow(canvas, width * 0.50f, height * 0.84f, width * 0.86f, 0x88B76BFF);
        flow(canvas, width, height, 0x66FF7BD7, 0.24f, 0.54f, 0.46f, 0.16f);
        flow(canvas, width, height, 0x5269D8FF, 0.48f, 0.76f, 0.48f, -0.18f);
        haze(canvas, width, height, 0x26FFFFFF, 0.22f, 0.50f, 1.10f);
    }

    private void champagneDawn(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF3B2B36, 0xFFC07A9F, 0xFF7E6548);
        glow(canvas, width * 0.18f, height * 0.18f, width * 0.86f, 0x8EFFD8A8);
        glow(canvas, width * 0.84f, height * 0.52f, width * 0.76f, 0x7AF5A1C7);
        glow(canvas, width * 0.46f, height * 0.86f, width * 0.80f, 0x68FFE9D1);
        flow(canvas, width, height, 0x5CFFE8C8, 0.22f, 0.44f, 0.38f, -0.12f);
        flow(canvas, width, height, 0x42FFC7E1, 0.50f, 0.78f, 0.42f, 0.18f);
        haze(canvas, width, height, 0x30FFFFFF, 0.16f, 0.46f, 0.94f);
    }

    private void glassGradient(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF335F78, 0xFF74689D, 0xFF8A6974);
        glow(canvas, width * 0.05f, height * 0.20f, width * 0.95f, 0x9D93F8FF);
        glow(canvas, width * 0.88f, height * 0.14f, width * 0.76f, 0x84EFC0FF);
        glow(canvas, width * 0.22f, height * 0.82f, width * 0.86f, 0x70A7FFE6);
        flow(canvas, width, height, 0x5CFFFFFF, 0.12f, 0.42f, 0.40f, -0.08f);
        flow(canvas, width, height, 0x38D2B4FF, 0.58f, 0.86f, 0.36f, 0.12f);
        arc(canvas, width, height, -0.14f, 0.70f, 0.68f, 0x70FFFFFF, 0.010f, 0.014f, 312f, 92f);
        bubble(canvas, width, height, 0.90f, 0.18f, 0.16f, 0x32FFFFFF, 0x80FFFFFF);
        bubble(canvas, width, height, 0.28f, 0.78f, 0.23f, 0x24FFFFFF, 0x55FFFFFF);
        haze(canvas, width, height, 0x20FFFFFF, 0.46f, 0.46f, 1.08f);
    }

    private void deepSpaceGlass(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF030817, 0xFF101A41, 0xFF070813);
        stars(canvas, width, height, 120);
        glow(canvas, width * 0.88f, height * 0.10f, width * 0.54f, 0x88D61574);
        glow(canvas, width * 0.88f, height * 0.78f, width * 0.76f, 0x7A116CFF);
        glow(canvas, width * 0.26f, height * 0.82f, width * 0.66f, 0x553F2BFF);
        beam(canvas, width, height, 0x4E7C42FF, 0.48f, -0.08f);
        beam(canvas, width, height, 0x38E74DFF, 0.58f, 0.06f);
        arc(canvas, width, height, 0.66f, 1.04f, 0.64f, 0x665F88FF, 0.010f, 0.016f, 206f, 112f);
        arc(canvas, width, height, 1.12f, 0.86f, 0.42f, 0x52B764FF, 0.008f, 0.018f, 172f, 82f);
        haze(canvas, width, height, 0x18FFFFFF, 0.44f, 0.48f, 1.04f);
    }

    private void polarLightGlass(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF365E74, 0xFF626C9B, 0xFF3F8B84);
        glow(canvas, width * 0.26f, height * 0.16f, width * 0.82f, 0xAFFFFFFF);
        glow(canvas, width * 0.82f, height * 0.24f, width * 0.78f, 0x80B5E6FF);
        glow(canvas, width * 0.72f, height * 0.80f, width * 0.82f, 0x73B9FFF2);
        flow(canvas, width, height, 0x48FFFFFF, 0.20f, 0.48f, 0.42f, -0.10f);
        flow(canvas, width, height, 0x32CBB8FF, 0.52f, 0.82f, 0.40f, 0.12f);
        bubble(canvas, width, height, 0.82f, 0.16f, 0.26f, 0x2CFFFFFF, 0x84FFFFFF);
        bubble(canvas, width, height, 0.04f, 0.98f, 0.28f, 0x20FFFFFF, 0x45FFFFFF);
        haze(canvas, width, height, 0x22FFFFFF, 0.48f, 0.50f, 1.10f);
    }

    private void neonCyber(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF020510, 0xFF101645, 0xFF060613);
        stars(canvas, width, height, 60);
        glow(canvas, width * 0.98f, height * 0.56f, width * 0.70f, 0x903F10D8);
        glow(canvas, width * 0.86f, height * 0.82f, width * 0.54f, 0x8AFF3DE0);
        glow(canvas, width * 0.14f, height * 0.28f, width * 0.58f, 0x5D00C8FF);
        beam(canvas, width, height, 0x6B1DCBFF, 0.36f, -0.06f);
        beam(canvas, width, height, 0x65E84DFF, 0.55f, 0.10f);
        current(canvas, width, height, 0x633BE4FF, 0.76f, -0.08f);
        arc(canvas, width, height, 1.08f, 0.90f, 0.48f, 0x75F04DFF, 0.011f, 0.018f, 190f, 104f);
        haze(canvas, width, height, 0x16FFFFFF, 0.42f, 0.52f, 1.02f);
    }

    private void warmMoonGlass(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF6A5352, 0xFF926C60, 0xFF725C82);
        glow(canvas, width * 0.80f, height * 0.16f, width * 0.64f, 0x78FFE7C8);
        glow(canvas, width * 0.16f, height * 0.82f, width * 0.86f, 0x72C9E6FF);
        glow(canvas, width * 0.72f, height * 0.70f, width * 0.76f, 0x65FFB1CF);
        bubble(canvas, width, height, 0.76f, 0.22f, 0.23f, 0x24FFFFFF, 0x55FFFFFF);
        bubble(canvas, width, height, 0.22f, 0.78f, 0.20f, 0x20FFFFFF, 0x44FFFFFF);
        flow(canvas, width, height, 0x42FFFFFF, 0.30f, 0.62f, 0.42f, 0.08f);
        arc(canvas, width, height, 0.82f, 1.02f, 0.56f, 0x54FFD6A6, 0.010f, 0.016f, 216f, 96f);
        haze(canvas, width, height, 0x28FFFFFF, 0.40f, 0.44f, 1.08f);
    }

    private void crystalSky(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF4B6388, 0xFF6877AE, 0xFF877DA3);
        glow(canvas, width * 0.18f, height * 0.20f, width * 0.82f, 0x8DC4FFFF);
        glow(canvas, width * 0.92f, height * 0.28f, width * 0.76f, 0x73BCA4FF);
        glow(canvas, width * 0.60f, height * 0.82f, width * 0.80f, 0x5CFFE4C8);
        bubble(canvas, width, height, 0.88f, 0.13f, 0.17f, 0x2AFFFFFF, 0x8EFFFFFF);
        bubble(canvas, width, height, 1.08f, 0.62f, 0.25f, 0x24BFD2FF, 0x68FFFFFF);
        arc(canvas, width, height, 0.96f, 0.18f, 0.23f, 0x72D2C2FF, 0.010f, 0.014f, 122f, 250f);
        arc(canvas, width, height, 0.92f, 0.62f, 0.20f, 0x55A6B7FF, 0.010f, 0.016f, 128f, 160f);
        flow(canvas, width, height, 0x35FFFFFF, 0.40f, 0.70f, 0.38f, -0.06f);
        haze(canvas, width, height, 0x24FFFFFF, 0.46f, 0.44f, 1.06f);
    }

    private void dreamPurple(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF6252B9, 0xFF8B70B5, 0xFF405BAC);
        glow(canvas, width * 0.18f, height * 0.16f, width * 0.78f, 0x77FFFFFF);
        glow(canvas, width * 0.80f, height * 0.78f, width * 0.78f, 0x88FF9CCB);
        glow(canvas, width * 0.30f, height * 0.70f, width * 0.64f, 0x60F4C4FF);
        flow(canvas, width, height, 0x54FFFFFF, 0.18f, 0.46f, 0.38f, -0.08f);
        flow(canvas, width, height, 0x48FFB6DF, 0.48f, 0.76f, 0.42f, 0.12f);
        bubble(canvas, width, height, 0.72f, 0.72f, 0.16f, 0x1CFFFFFF, 0x5FFFFFFF);
        haze(canvas, width, height, 0x20FFFFFF, 0.34f, 0.42f, 1.08f);
    }

    private void skyMint(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF2E6675, 0xFF5E8585, 0xFF7D785E);
        glow(canvas, width * 0.24f, height * 0.10f, width * 0.86f, 0xB8FFFFFF);
        glow(canvas, width * 0.74f, height * 0.48f, width * 0.82f, 0x66B3F7FF);
        glow(canvas, width * 0.46f, height * 0.88f, width * 0.78f, 0x5BFFF0BD);
        flow(canvas, width, height, 0x45FFFFFF, 0.18f, 0.48f, 0.42f, -0.10f);
        flow(canvas, width, height, 0x34B5F6FF, 0.44f, 0.74f, 0.34f, 0.12f);
        haze(canvas, width, height, 0x26FFFFFF, 0.52f, 0.46f, 1.10f);
    }

    private void forestMist(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF08241F, 0xFF1D5448, 0xFF123930);
        glow(canvas, width * 0.18f, height * 0.14f, width * 0.72f, 0x5250A65E);
        glow(canvas, width * 0.78f, height * 0.28f, width * 0.72f, 0x557DB852);
        glow(canvas, width * 0.46f, height * 0.88f, width * 0.76f, 0x5057D9A7);
        flow(canvas, width, height, 0x385D9D55, 0.10f, 0.32f, 0.32f, 0.14f);
        flow(canvas, width, height, 0x497BD086, 0.50f, 0.72f, 0.38f, -0.10f);
        arc(canvas, width, height, 0.08f, 0.24f, 0.34f, 0x4FA6D36A, 0.014f, 0.018f, 210f, 82f);
        arc(canvas, width, height, 0.96f, 0.92f, 0.42f, 0x4A9BC871, 0.014f, 0.018f, 210f, 88f);
        haze(canvas, width, height, 0x18FFFFFF, 0.30f, 0.46f, 1.00f);
    }

    private void daylightMinimal(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF4A5C6C, 0xFF637482, 0xFF747E86);
        glow(canvas, width * 0.14f, height * 0.12f, width * 0.82f, 0xB0FFFFFF);
        glow(canvas, width * 0.82f, height * 0.72f, width * 0.74f, 0x42C5D8E8);
        flow(canvas, width, height, 0x36FFFFFF, 0.28f, 0.58f, 0.34f, 0.06f);
        arc(canvas, width, height, 0.66f, 1.12f, 0.48f, 0x30A9B9CA, 0.010f, 0.014f, 206f, 104f);
        haze(canvas, width, height, 0x22FFFFFF, 0.44f, 0.46f, 1.06f);
    }

    private void deepSea(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF031927, 0xFF0D4C64, 0xFF092231);
        stars(canvas, width, height, 50);
        glow(canvas, width * 0.80f, height * 0.28f, width * 0.70f, 0x3848D6FF);
        glow(canvas, width * 0.18f, height * 0.82f, width * 0.76f, 0x404DA3C8);
        current(canvas, width, height, 0x56A8D7E8, 0.54f, -0.08f);
        current(canvas, width, height, 0x3DE2F8FF, 0.64f, 0.04f);
        arc(canvas, width, height, 0.22f, 0.94f, 0.48f, 0x5CBFEAFF, 0.010f, 0.016f, 210f, 100f);
        haze(canvas, width, height, 0x16FFFFFF, 0.42f, 0.56f, 1.02f);
    }

    private void violetSmoke(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF070515, 0xFF2C1355, 0xFF0B0722);
        glow(canvas, width * 0.54f, height * 0.46f, width * 0.46f, 0x9A9A38FF);
        glow(canvas, width * 0.62f, height * 0.26f, width * 0.54f, 0x6A4422FF);
        glow(canvas, width * 0.44f, height * 0.74f, width * 0.48f, 0x724DB0FF);
        flow(canvas, width, height, 0x50B065FF, 0.18f, 0.48f, 0.28f, 0.12f);
        flow(canvas, width, height, 0x3DDC72FF, 0.52f, 0.82f, 0.34f, -0.08f);
        haze(canvas, width, height, 0x20FFFFFF, 0.52f, 0.46f, 0.92f);
    }

    private void roseVeil(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF7E566D, 0xFF936A98, 0xFF5C7790);
        glow(canvas, width * 0.84f, height * 0.18f, width * 0.78f, 0x6CFFC3E6);
        glow(canvas, width * 0.44f, height * 0.62f, width * 0.72f, 0x54E6B2FF);
        glow(canvas, width * 0.16f, height * 0.86f, width * 0.66f, 0x42BFF7FF);
        flow(canvas, width, height, 0x48FFFFFF, 0.42f, 0.70f, 0.34f, -0.04f);
        arc(canvas, width, height, 0.38f, 0.62f, 0.36f, 0x44FFA6E7, 0.010f, 0.016f, 322f, 124f);
        haze(canvas, width, height, 0x28FFFFFF, 0.42f, 0.44f, 1.10f);
    }

    private void emeraldAurora(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF021513, 0xFF0E4A44, 0xFF020B0B);
        stars(canvas, width, height, 70);
        glow(canvas, width * 0.50f, height * 0.46f, width * 0.76f, 0x6520D88B);
        glow(canvas, width * 0.80f, height * 0.30f, width * 0.66f, 0x553BDCA4);
        current(canvas, width, height, 0x5A27F0A3, 0.34f, -0.08f);
        current(canvas, width, height, 0x4532C4FF, 0.54f, 0.08f);
        arc(canvas, width, height, 0.74f, 0.92f, 0.54f, 0x5A47F5BD, 0.009f, 0.018f, 198f, 100f);
        haze(canvas, width, height, 0x16FFFFFF, 0.44f, 0.46f, 1.02f);
    }

    private void blueSilk(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF37677B, 0xFF4E8EA3, 0xFF4B7896);
        glow(canvas, width * 0.20f, height * 0.14f, width * 0.84f, 0x7FFFFFFF);
        glow(canvas, width * 0.84f, height * 0.38f, width * 0.72f, 0x66BDEEFF);
        current(canvas, width, height, 0x62FFFFFF, 0.26f, -0.08f);
        current(canvas, width, height, 0x4FBDEBFF, 0.70f, 0.12f);
        arc(canvas, width, height, 0.80f, 0.08f, 0.42f, 0x4EFFFFFF, 0.010f, 0.018f, 128f, 108f);
        haze(canvas, width, height, 0x22FFFFFF, 0.46f, 0.44f, 1.08f);
    }

    private void peachDawn(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF885C54, 0xFFA2725D, 0xFF87658A);
        glow(canvas, width * 0.18f, height * 0.12f, width * 0.86f, 0x7EFFFFFF);
        glow(canvas, width * 0.78f, height * 0.76f, width * 0.78f, 0x60F28BFF);
        glow(canvas, width * 0.30f, height * 0.84f, width * 0.76f, 0x55FFD58A);
        flow(canvas, width, height, 0x42FFFFFF, 0.20f, 0.52f, 0.42f, 0.08f);
        flow(canvas, width, height, 0x38F2B4FF, 0.48f, 0.80f, 0.36f, -0.10f);
        haze(canvas, width, height, 0x24FFFFFF, 0.46f, 0.42f, 1.08f);
    }

    private void graphiteSmoke(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF090B10, 0xFF262D36, 0xFF11151C);
        glow(canvas, width * 0.20f, height * 0.82f, width * 0.70f, 0x3A8CA5C8);
        glow(canvas, width * 0.72f, height * 0.52f, width * 0.62f, 0x35586683);
        flow(canvas, width, height, 0x3A8998A8, 0.46f, 0.66f, 0.34f, -0.08f);
        flow(canvas, width, height, 0x30FFFFFF, 0.58f, 0.78f, 0.30f, 0.10f);
        current(canvas, width, height, 0x335D7184, 0.72f, -0.04f);
        haze(canvas, width, height, 0x14FFFFFF, 0.36f, 0.52f, 0.96f);
    }

    private void pastelPrism(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF63769C, 0xFF88699B, 0xFF5A8E96);
        glow(canvas, width * 0.18f, height * 0.20f, width * 0.84f, 0x64BDEBFF);
        glow(canvas, width * 0.78f, height * 0.22f, width * 0.78f, 0x66FFB5D4);
        glow(canvas, width * 0.62f, height * 0.84f, width * 0.76f, 0x58FFF0A8);
        flow(canvas, width, height, 0x45FFFFFF, 0.24f, 0.56f, 0.40f, 0.08f);
        arc(canvas, width, height, 0.82f, 0.06f, 0.50f, 0x52FFB6F0, 0.010f, 0.016f, 112f, 124f);
        arc(canvas, width, height, 0.18f, 1.02f, 0.48f, 0x48B5F2FF, 0.010f, 0.016f, 216f, 100f);
        haze(canvas, width, height, 0x24FFFFFF, 0.48f, 0.46f, 1.10f);
    }

    private void midnightMoon(Canvas canvas, int width, int height) {
        background(canvas, width, height, 0xFF030A23, 0xFF15165A, 0xFF050916);
        stars(canvas, width, height, 58);
        glow(canvas, width * 0.82f, height * 0.62f, width * 0.54f, 0x613C21B9);
        glow(canvas, width * 0.74f, height * 0.84f, width * 0.44f, 0x6B622CFF);
        bubble(canvas, width, height, 0.78f, 0.56f, 0.23f, 0x233D22C6, 0x40A78CFF);
        arc(canvas, width, height, 0.86f, 0.88f, 0.42f, 0x6E9A65FF, 0.010f, 0.018f, 198f, 104f);
        haze(canvas, width, height, 0x14FFFFFF, 0.42f, 0.48f, 1.00f);
    }

    private void cyanCrystal(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF16849A, 0xFF238DAA, 0xFF4D68AA);
        glow(canvas, width * 0.18f, height * 0.14f, width * 0.80f, 0x84FFFFFF);
        glow(canvas, width * 0.82f, height * 0.72f, width * 0.84f, 0x795C8DFF);
        flow(canvas, width, height, 0x55FFFFFF, 0.28f, 0.58f, 0.36f, -0.10f);
        current(canvas, width, height, 0x6B13B8FF, 0.62f, 0.08f);
        bubble(canvas, width, height, 0.96f, 0.48f, 0.22f, 0x1FFFFFFF, 0x70FFFFFF);
        arc(canvas, width, height, 0.98f, 0.48f, 0.24f, 0x72FFFFFF, 0.010f, 0.014f, 138f, 210f);
        haze(canvas, width, height, 0x24FFFFFF, 0.42f, 0.44f, 1.08f);
    }

    private void lavenderCrystal(Canvas canvas, int width, int height) {
        lightBackground(canvas, width, height, 0xFF715CAC, 0xFF8974B5, 0xFF715B9B);
        glow(canvas, width * 0.16f, height * 0.18f, width * 0.82f, 0x66FFFFFF);
        glow(canvas, width * 0.84f, height * 0.78f, width * 0.78f, 0x78C79BFF);
        glow(canvas, width * 0.18f, height * 0.82f, width * 0.60f, 0x48FFB5E3);
        flow(canvas, width, height, 0x46FFFFFF, 0.36f, 0.64f, 0.38f, -0.08f);
        bubble(canvas, width, height, 1.02f, 0.82f, 0.24f, 0x22B69CFF, 0x6FFFFFFF);
        arc(canvas, width, height, 0.90f, 0.72f, 0.30f, 0x64FFFFFF, 0.010f, 0.018f, 132f, 160f);
        haze(canvas, width, height, 0x28FFFFFF, 0.48f, 0.46f, 1.10f);
    }

    private void solid(Canvas canvas, int width, int height) {
        paint.setShader(null);
        paint.setColor(Setting.getBuiltInWallColor(wall));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void background(Canvas canvas, int width, int height, int top, int center, int bottom) {
        paint.setShader(new LinearGradient(0, 0, width, height, new int[]{top, center, bottom}, new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(width, 0, 0, height, 0x44000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void lightBackground(Canvas canvas, int width, int height, int top, int center, int bottom) {
        paint.setShader(new LinearGradient(0, 0, width, height, new int[]{top, center, bottom}, new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(width, 0, 0, height, 0x16000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void glow(Canvas canvas, float cx, float cy, float radius, int color) {
        paint.setShader(new RadialGradient(cx, cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void flow(Canvas canvas, int width, int height, int color, float start, float end, float thickness, float phase) {
        Path path = new Path();
        float p = height * phase;
        path.moveTo(-width * 0.18f, height * start + p);
        path.cubicTo(width * 0.22f, height * (start - 0.18f) - p, width * 0.54f, height * (end - 0.28f), width * 1.18f, height * (end - 0.12f) + p);
        path.lineTo(width * 1.18f, height * (end + thickness * 0.36f) + p);
        path.cubicTo(width * 0.58f, height * (end + thickness * 0.12f), width * 0.28f, height * (start + thickness * 0.58f) + p, -width * 0.18f, height * (start + thickness * 0.42f) - p);
        path.close();
        paint.setShader(new LinearGradient(0, height * start, width, height * end, new int[]{0x00FFFFFF, color, 0x00FFFFFF}, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(18f, width * 0.030f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(path, paint);
        paint.setMaskFilter(null);
    }

    private void current(Canvas canvas, int width, int height, int color, float y, float phase) {
        Path path = new Path();
        float p = height * phase;
        path.moveTo(-width * 0.12f, height * y + p);
        path.cubicTo(width * 0.16f, height * (y - 0.16f), width * 0.38f, height * (y + 0.20f), width * 0.64f, height * (y + 0.02f));
        path.cubicTo(width * 0.86f, height * (y - 0.12f), width * 1.04f, height * (y + 0.02f), width * 1.14f, height * (y - 0.04f));
        path.lineTo(width * 1.14f, height * (y + 0.16f));
        path.cubicTo(width * 0.78f, height * (y + 0.30f), width * 0.44f, height * (y + 0.04f), -width * 0.12f, height * (y + 0.22f));
        path.close();
        paint.setShader(new LinearGradient(0, height * y, width, height * (y + 0.18f), new int[]{0x00FFFFFF, color, 0x12FFFFFF}, null, Shader.TileMode.CLAMP));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(16f, width * 0.026f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(path, paint);
        paint.setMaskFilter(null);
    }

    private void beam(Canvas canvas, int width, int height, int color, float y, float phase) {
        Path path = new Path();
        float p = height * phase;
        path.moveTo(-width * 0.16f, height * (y + 0.08f) + p);
        path.cubicTo(width * 0.22f, height * (y - 0.05f) + p, width * 0.52f, height * (y + 0.06f) - p, width * 1.14f, height * (y - 0.22f) + p);
        path.lineTo(width * 1.14f, height * (y - 0.08f) + p);
        path.cubicTo(width * 0.60f, height * (y + 0.20f) - p, width * 0.28f, height * (y + 0.08f) + p, -width * 0.16f, height * (y + 0.22f) - p);
        path.close();
        paint.setShader(new LinearGradient(0, height * (y - 0.06f), width, height * (y + 0.14f), new int[]{0x00FFFFFF, color, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(10f, width * 0.022f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(path, paint);
        paint.setMaskFilter(null);
    }

    private void haze(Canvas canvas, int width, int height, int color, float cx, float cy, float scale) {
        paint.setShader(new RadialGradient(width * cx, height * cy, Math.max(width, height) * scale, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void arc(Canvas canvas, int width, int height, float cx, float cy, float radius, int color, float stroke, float blur) {
        arc(canvas, width, height, cx, cy, radius, color, stroke, blur, 205f, 110f);
    }

    private void arc(Canvas canvas, int width, int height, float cx, float cy, float radius, int color, float stroke, float blur, float startAngle, float sweepAngle) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, width * stroke));
        paint.setShader(new LinearGradient(0, height * (cy - radius), width, height * cy, new int[]{0x00FFFFFF, color, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(6f, width * blur), BlurMaskFilter.Blur.NORMAL));
        float r = width * radius;
        RectF rect = new RectF(width * cx - r, height * cy - r, width * cx + r, height * cy + r);
        canvas.drawArc(rect, startAngle, sweepAngle, false, paint);
        paint.setMaskFilter(null);
        paint.setShader(null);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void bubble(Canvas canvas, int width, int height, float cx, float cy, float radius, int fillColor, int rimColor) {
        float x = width * cx;
        float y = height * cy;
        float r = width * radius;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x - r * 0.28f, y - r * 0.34f, r * 1.25f, new int[]{0x82FFFFFF, fillColor, 0x08FFFFFF}, new float[]{0f, 0.44f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, width * 0.004f));
        paint.setShader(new LinearGradient(x - r, y - r, x + r, y + r, new int[]{rimColor, 0x18FFFFFF, rimColor}, null, Shader.TileMode.CLAMP));
        paint.setMaskFilter(new BlurMaskFilter(Math.max(3f, width * 0.006f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawCircle(x, y, r * 0.98f, paint);
        paint.setMaskFilter(null);
        paint.setShader(null);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void stars(Canvas canvas, int width, int height, int count) {
        paint.setShader(null);
        paint.setMaskFilter(null);
        for (int i = 0; i < count; i++) {
            float x = width * noise(i * 41 + wall * 31);
            float y = height * noise(i * 67 + wall * 37);
            int alpha = 20 + (int) (noise(i * 97 + wall * 43) * 40f);
            paint.setColor(Color.argb(alpha, 210, 240, 255));
            canvas.drawPoint(x, y, paint);
        }
    }

    private void vignette(Canvas canvas, int width, int height) {
        float radius = Math.max(width, height) * 0.78f;
        paint.setShader(new RadialGradient(width / 2f, height / 2f, radius, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, 0, height, 0x14000000, 0x52000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void lightVignette(Canvas canvas, int width, int height) {
        float radius = Math.max(width, height) * 0.86f;
        paint.setShader(new RadialGradient(width / 2f, height * 0.42f, radius, 0x00000000, 0x22000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, 0, height, 0x00FFFFFF, 0x18000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void lightReadability(Canvas canvas, int width, int height) {
        int top = isHighKeyWall() ? 0x3A000000 : 0x28000000;
        int center = isHighKeyWall() ? 0x46000000 : 0x34000000;
        int bottom = isHighKeyWall() ? 0x52000000 : 0x40000000;
        paint.setShader(new LinearGradient(0, 0, 0, height, new int[]{top, center, bottom}, new float[]{0f, 0.54f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void grain(Canvas canvas, int width, int height) {
        paint.setShader(null);
        int count = Math.max(180, Math.min(620, width * height / 4200));
        for (int i = 0; i < count; i++) {
            float x = width * noise(i * 37 + wall * 19);
            float y = height * noise(i * 53 + wall * 23);
            int alpha = 5 + (int) (noise(i * 89 + wall * 29) * 9f);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawPoint(x, y, paint);
        }
    }

    private boolean isLightWall() {
        return wall == Setting.WALL_GLASS_GRADIENT
                || wall == Setting.WALL_POLAR_LIGHT_GLASS
                || wall == Setting.WALL_WARM_MOON_GLASS
                || wall == Setting.WALL_CRYSTAL_SKY
                || wall == Setting.WALL_DREAM_PURPLE
                || wall == Setting.WALL_SKY_MINT
                || wall == Setting.WALL_DAYLIGHT_MINIMAL
                || wall == Setting.WALL_ROSE_VEIL
                || wall == Setting.WALL_BLUE_SILK
                || wall == Setting.WALL_PEACH_DAWN
                || wall == Setting.WALL_PASTEL_PRISM
                || wall == Setting.WALL_CYAN_CRYSTAL
                || wall == Setting.WALL_LAVENDER_CRYSTAL;
    }

    private boolean isHighKeyWall() {
        return wall == Setting.WALL_GLASS_GRADIENT
                || wall == Setting.WALL_POLAR_LIGHT_GLASS
                || wall == Setting.WALL_SKY_MINT
                || wall == Setting.WALL_DAYLIGHT_MINIMAL
                || wall == Setting.WALL_ROSE_VEIL
                || wall == Setting.WALL_PASTEL_PRISM
                || wall == Setting.WALL_CRYSTAL_SKY
                || wall == Setting.WALL_LAVENDER_CRYSTAL;
    }

    private float noise(int value) {
        int x = value;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return (x & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }
}
