package dev.enchantglint.core;

/** Pure-Java colour helpers. Colours are 0xRRGGBB ints unless noted. */
public final class ColorUtil {
    private ColorUtil() {}

    public static int r(int c) { return (c >> 16) & 0xFF; }
    public static int g(int c) { return (c >> 8) & 0xFF; }
    public static int b(int c) { return c & 0xFF; }
    public static int rgb(int r, int g, int b) { return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b); }
    public static int clamp255(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    public static int lerp(int a, int b, float t) {
        return rgb(Math.round(r(a) + (r(b) - r(a)) * t),
                   Math.round(g(a) + (g(b) - g(a)) * t),
                   Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** Parses "#RRGGBB", "RRGGBB" or "#RGB". Returns -1 if invalid. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                return rgb(r, g, b);
            }
            if (s.length() == 6) return Integer.parseInt(s, 16);
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    public static String toHex(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    /** h in [0,1), s,v in [0,1]. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float h6 = h * 6f;
        int i = (int) Math.floor(h6);
        float f = h6 - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** Returns {h, s, v} each in [0,1]. */
    public static float[] rgbToHsv(int c) {
        float r = r(c) / 255f, g = g(c) / 255f, b = b(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[] { h, max == 0 ? 0 : d / max, max };
    }
}
