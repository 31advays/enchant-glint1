package dev.enchantglint.client;

/**
 * Tuning constants that can only be set properly by looking at the result in game.
 * Vanilla maps the two glint textures with very different UV scales (held items ~8x, armour ~0.16x),
 * so the same plaid needs a different repeat count per slot to look the same size on screen.
 * UNTESTED IN GAME: these are starting guesses; adjust after the first run.
 */
public final class Calibration {
    private Calibration() {}

    /** Multiplier on plaid repeats for the armour glint texture. */
    public static final float ARMOR_CYCLE_MULT = 1.0f;
    /** Multiplier on plaid repeats for the held / GUI / dropped item glint texture. */
    public static final float ITEM_CYCLE_MULT = 1.0f;
    /** Brightness gain applied on top of opacity (vanilla blend is additive). */
    public static final float ARMOR_GAIN = 1.15f;
    public static final float ITEM_GAIN = 1.10f;
}
