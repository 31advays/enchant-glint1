package dev.enchantglint.core;

/**
 * The 15 built-in presets. Presets differ in colour and colour-flow only; the plaid
 * pattern and vanilla scroll motion are shared (user request: "just the colours").
 */
public enum GlintPreset {
    VANILLA("Vanilla",      0xA253FD, 0xA253FD, Style.GRADIENT, 0, 1.00f),
    RAINBOW("Rainbow",      0xFF3B6B, 0x3BD1FF, Style.RAINBOW,  0, 1.00f),
    ICE("Ice",              0x5EC8FF, 0xEAFCFF, Style.GRADIENT, 0, 1.00f),
    INFERNO("Inferno",      0xFF3D00, 0xFFC21A, Style.GRADIENT, 1, 1.00f),
    GOLDEN("Golden",        0xFFB000, 0xFFF1A8, Style.GRADIENT, 0, 1.00f),
    SHADOW("Shadow",        0x8A4FD0, 0x2A0F4A, Style.GRADIENT, 0, 0.85f),
    WHITE("White",          0xFFFFFF, 0xCFD8E8, Style.GRADIENT, 0, 0.90f),
    EMERALD("Emerald",      0x0FA85A, 0x9DFFC4, Style.GRADIENT, 0, 1.00f),
    NEON("Neon",            0xFF3DF0, 0x2EF2FF, Style.GRADIENT, 0, 1.00f),
    GALAXY("Galaxy",        0x6D4BFF, 0xFF8CF5, Style.GRADIENT, 1, 1.00f),
    CRYSTAL("Crystal",      0xCFEAFF, 0xE1B8FF, Style.GRADIENT, 0, 1.00f),
    MINIMAL("Minimal",      0xD8DEEA, 0xD8DEEA, Style.GRADIENT, 0, 0.50f),
    ROSE("Rose",            0xFF8FB0, 0xFFD1DE, Style.GRADIENT, 0, 1.00f),
    HOT_PINK("Hot Pink",    0xFF1493, 0xFF6EC7, Style.GRADIENT, 0, 1.00f),
    COTTON_CANDY("Cotton Candy", 0xFF9BD2, 0x8FD3FF, Style.GRADIENT, 0, 1.00f);

    public enum Style { GRADIENT, RAINBOW }

    public final String label;
    public final int color1, color2;
    public final Style style;
    /** 0 = diagonal flow, 1 = vertical flow. Colour flow only, not pattern. */
    public final int axis;
    /** Preset-specific brightness multiplier (Minimal is deliberately subtle). */
    public final float intensity;

    GlintPreset(String label, int c1, int c2, Style style, int axis, float intensity) {
        this.label = label; this.color1 = c1; this.color2 = c2;
        this.style = style; this.axis = axis; this.intensity = intensity;
    }

    public static GlintPreset byName(String name) {
        if (name != null) for (GlintPreset p : values()) if (p.name().equals(name)) return p;
        return VANILLA;
    }
}
