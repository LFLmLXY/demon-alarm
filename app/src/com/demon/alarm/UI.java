package com.demon.alarm;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Tiny styling helpers so every screen is built programmatically (no layout XML keeps the build
 * toolchain simple).
 *
 * Palette: warm cream paper + coral. The old near-black theme made the app look like a terminal,
 * which nobody wants to open in the morning. The ringing screen keeps a deep red gradient because
 * it wakes you up at 4am and needs to be unmistakable.
 */
public final class UI {
    /* ---------- light theme (main / edit / settings) ---------- */
    public static final int BG = 0xFFFDF6F1;
    public static final int BG_TOP = 0xFFFFF4EC;
    public static final int BG_BOTTOM = 0xFFFBE5DA;
    public static final int CARD = 0xFFFFFFFF;
    public static final int CARD2 = 0xFFF7EFE9;
    public static final int STROKE = 0xFFEFDFD5;
    public static final int RED = 0xFFEA5237;
    public static final int RED_DARK = 0xFFD8442B;
    public static final int RED_SOFT = 0xFFFFE9E2;
    public static final int TEXT = 0xFF221B1A;
    public static final int DIM = 0xFF8C7C74;
    public static final int GREEN = 0xFF1E9E64;
    public static final int GREEN_SOFT = 0xFFE1F5EA;
    public static final int YELLOW = 0xFFF5A524;
    public static final int BANNER_BG = 0xFFFFF4E3;
    public static final int BANNER_STROKE = 0xFFF2D2A2;
    public static final int BANNER_TEXT = 0xFF8A5300;
    public static final int LINK = 0xFF2F7AE5;
    public static final int ON_ACCENT = 0xFFFFFFFF;

    /* ---------- ringing screen: night crimson ---------- */
    public static final int RING_TEXT = 0xFFFDF3EF;
    public static final int RING_DIM = 0xB3FDF3EF;
    public static final int RING_ACCENT = 0xFFFF9E8A;
    public static final int RING_SOFT = 0x33FFFFFF;
    public static final int RING_LINE = 0x4DFFFFFF;
    public static final int RING_OK = 0xFF6EE7A8;

    private UI() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    public static GradientDrawable round(int color, Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(int color, int strokeColor, Context c, float radiusDp, float strokeDp) {
        GradientDrawable g = round(color, c, radiusDp);
        g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static GradientDrawable roundAll(int color, Context c, float radiusDp,
                                            int strokeColor, float strokeDp,
                                            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        GradientDrawable g = round(color, c, 0);
        g.setCornerRadii(new float[]{
                dp(c, topLeft), dp(c, topLeft),
                dp(c, topRight), dp(c, topRight),
                dp(c, bottomRight), dp(c, bottomRight),
                dp(c, bottomLeft), dp(c, bottomLeft)});
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    /** warm vertical gradient used as the page background */
    public static GradientDrawable bg(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM});
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return g;
    }

    /** deep crimson gradient for the ringing screen */
    public static GradientDrawable ringBg(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3D1017, 0xFF180608, 0xFF2E0A1E});
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return g;
    }

    /** standard white card: rounded + hairline border */
    public static GradientDrawable card(Context c) {
        return roundStroke(CARD, STROKE, c, 20, 1);
    }

    /** unselected chip */
    public static GradientDrawable chipOff(Context c) {
        return roundStroke(CARD2, STROKE, c, 12, 1);
    }

    public static android.widget.Button mkButton(Context c, String label, int bgColor, int textColor) {
        android.widget.Button b = new android.widget.Button(c);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setBackground(round(bgColor, c, 18));
        b.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        return b;
    }
}
