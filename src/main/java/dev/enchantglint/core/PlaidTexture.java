package dev.enchantglint.core;

/**
 * Original procedural woven-plaid glint generator (no third-party texture data).
 *
 * Output is a seamless, tileable ARGB int[] (alpha always 255). The glint blend in vanilla is
 * additive (SRC_COLOR, ONE), so black = no glint and bright = glint; the map is therefore
 * "colour * pattern intensity".
 *
 * Independence of controls:
 *  - strength : pattern coverage (threshold on the plaid intensity map)
 *  - opacity  : uniform RGB multiplier (does not change coverage)
 *  - scale    : integer number of plaid repeats per tile (keeps the tile seamless)
 */
public final class PlaidTexture {
    private PlaidTexture() {}

    public static final int SIZE = 128;
    /** Threads per plaid repeat along each axis. */
    private static final int THREADS = 16;

    /** Immutable key; equal keys produce identical textures, so it doubles as a cache key. */
    public record Params(int cycles, int color1, int color2, GlintPreset.Style style, int axis,
                         float strength, float opacity, float gain) {}

    public static int cyclesForScale(float scale, float slotMultiplier) {
        int c = Math.round(2f * scale * slotMultiplier);
        return c < 1 ? 1 : Math.min(c, 8);
    }

    public static int[] generate(Params p) {
        final int n = SIZE;
        int[] out = new int[n * n];
        final float threshold = 0.55f * (1f - clamp01(p.strength()));
        final float inv = 1f / (1f - threshold);
        final float amp = clamp01(p.opacity()) * p.gain();
        for (int y = 0; y < n; y++) {
            float v = (float) y / n * p.cycles();
            for (int x = 0; x < n; x++) {
                float u = (float) x / n * p.cycles();
                float g = intensity(u, v);
                g = clamp01((g - threshold) * inv);
                int col = colourAt(p, u, v);
                float k = g * amp;
                int r = Math.min(255, Math.round(ColorUtil.r(col) * k));
                int gg = Math.min(255, Math.round(ColorUtil.g(col) * k));
                int b = Math.min(255, Math.round(ColorUtil.b(col) * k));
                out[y * n + x] = 0xFF000000 | (r << 16) | (gg << 8) | b;
            }
        }
        return out;
    }

    // ---- pattern -------------------------------------------------------------------------

    /** Plaid intensity in [0,1] at plaid-space coords (period 1 in both axes). */
    static float intensity(float u, float v) {
        float h = band(frac(u));
        float w = band(frac(v));
        float base = 1f - (1f - h) * (1f - w);                // bands overlap -> brighter crossings

        int tu = (int) Math.floor(u * THREADS), tv = (int) Math.floor(v * THREADS);
        boolean over = ((tu + tv) & 3) < 2;                   // 2/2 twill diagonal
        float fu = frac(u * THREADS), fv = frac(v * THREADS);
        float thread = over ? edge(fu) : edge(fv);            // rounded thread shading
        float weave = 0.62f + 0.38f * thread;
        float floor = 0.20f * (0.5f + 0.5f * (over ? edge(fv) : edge(fu)));
        return clamp01(base * weave + floor);
    }

    /** 1-D plaid band profile: one wide band, one medium, one thin hairline. */
    private static float band(float t) {
        float a = pulse(t, 0.02f, 0.30f, 0.05f) * 0.95f;
        float b = pulse(t, 0.46f, 0.58f, 0.03f) * 0.70f;
        float c = pulse(t, 0.80f, 0.84f, 0.02f) * 0.50f;
        return Math.max(a, Math.max(b, c));
    }

    private static float pulse(float t, float lo, float hi, float soft) {
        float up = smooth((t - (lo - soft)) / soft);
        float dn = smooth(((hi + soft) - t) / soft);
        return Math.min(up, dn);
    }

    private static float edge(float f) { return (float) Math.sin(Math.PI * f); }
    private static float smooth(float x) { x = clamp01(x); return x * x * (3 - 2 * x); }
    private static float frac(float x) { return x - (float) Math.floor(x); }
    private static float clamp01(float x) { return x < 0 ? 0 : Math.min(x, 1f); }

    // ---- colour --------------------------------------------------------------------------

    private static int colourAt(Params p, float u, float v) {
        float s = p.axis() == 1 ? v : (u + v);
        if (p.style() == GlintPreset.Style.RAINBOW) {
            return ColorUtil.hsvToRgb(frac(u + v), 0.85f, 1f);
        }
        float t = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * s);
        return ColorUtil.lerp(p.color1(), p.color2(), t);
    }
}
