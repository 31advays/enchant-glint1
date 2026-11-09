package dev.enchantglint.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** All persisted user settings. Plain fields so Gson can (de)serialise without adapters. */
public final class GlintSettings {
    public enum ItemMode { ARMOR_ONLY, SELECTED }

    public static final int MAX_SAVED_COLOURS = 24;
    public static final int MAX_SAVED_PRESETS = 16;

    public boolean enabled = true;
    public String preset = GlintPreset.VANILLA.name();

    // custom colours (override preset colours when customEnabled)
    public boolean customEnabled = false;
    public int customColor1 = 0xA253FD;
    public int customColor2 = 0x5EC8FF;
    public List<Integer> savedColours = new ArrayList<>();
    public List<SavedPreset> savedPresets = new ArrayList<>();

    // tuning (all independent)
    public float strength = 0.80f;   // 0..1  pattern coverage
    public float opacity = 1.00f;    // 0..1  brightness multiplier
    public float scale = 1.00f;      // 0.5..2.0 plaid size
    public boolean vanillaSpeed = true;
    public float speed = 0.50f;      // 0..1 maps to Minecraft's glint speed option when vanillaSpeed == false

    // item selection
    public ItemMode itemMode = ItemMode.ARMOR_ONLY;
    public boolean helmet = true, chestplate = true, leggings = true, boots = true;
    public Set<String> categories = new LinkedHashSet<>();   // ItemRouter.Category names
    public Set<String> items = new LinkedHashSet<>();        // item ids e.g. "minecraft:diamond_sword"

    /** A named snapshot of the look (colours + tuning), independent of item selection. */
    public static final class SavedPreset {
        public String name = "Custom";
        public String preset = GlintPreset.VANILLA.name();
        public boolean customEnabled;
        public int color1, color2;
        public float strength, opacity, scale, speed;
        public boolean vanillaSpeed;
    }

    public GlintPreset presetEnum() { return GlintPreset.byName(preset); }

    public int effectiveColor1() { return customEnabled ? customColor1 : presetEnum().color1; }
    public int effectiveColor2() { return customEnabled ? customColor2 : presetEnum().color2; }
    public GlintPreset.Style effectiveStyle() { return customEnabled ? GlintPreset.Style.GRADIENT : presetEnum().style; }
    public int effectiveAxis() { return customEnabled ? 0 : presetEnum().axis; }
    public float effectiveIntensity() { return customEnabled ? 1f : presetEnum().intensity; }

    public SavedPreset snapshot(String name) {
        SavedPreset s = new SavedPreset();
        s.name = name; s.preset = preset; s.customEnabled = customEnabled;
        s.color1 = customColor1; s.color2 = customColor2;
        s.strength = strength; s.opacity = opacity; s.scale = scale; s.speed = speed;
        s.vanillaSpeed = vanillaSpeed;
        return s;
    }

    public void apply(SavedPreset s) {
        preset = s.preset; customEnabled = s.customEnabled;
        customColor1 = s.color1; customColor2 = s.color2;
        strength = s.strength; opacity = s.opacity; scale = s.scale; speed = s.speed;
        vanillaSpeed = s.vanillaSpeed;
        sanitize();
    }

    public void saveColour(int rgb) {
        rgb &= 0xFFFFFF;
        savedColours.remove((Integer) rgb);
        savedColours.add(0, rgb);
        while (savedColours.size() > MAX_SAVED_COLOURS) savedColours.remove(savedColours.size() - 1);
    }

    public void savePreset(String name) {
        savedPresets.removeIf(p -> p.name.equals(name));
        savedPresets.add(0, snapshot(name));
        while (savedPresets.size() > MAX_SAVED_PRESETS) savedPresets.remove(savedPresets.size() - 1);
    }

    /** Clamp everything and repair nulls; call after loading or editing. */
    public void sanitize() {
        if (GlintPreset.byName(preset).name().equals(preset) == false) preset = GlintPreset.VANILLA.name();
        customColor1 &= 0xFFFFFF; customColor2 &= 0xFFFFFF;
        strength = clamp(strength, 0f, 1f);
        opacity = clamp(opacity, 0f, 1f);
        scale = clamp(scale, 0.5f, 2.0f);
        speed = clamp(speed, 0f, 1f);
        if (itemMode == null) itemMode = ItemMode.ARMOR_ONLY;
        if (savedColours == null) savedColours = new ArrayList<>();
        if (savedPresets == null) savedPresets = new ArrayList<>();
        if (categories == null) categories = new LinkedHashSet<>();
        if (items == null) items = new LinkedHashSet<>();
        savedPresets.removeIf(p -> p == null || p.name == null);
        for (SavedPreset p : savedPresets) {
            p.strength = clamp(p.strength, 0f, 1f); p.opacity = clamp(p.opacity, 0f, 1f);
            p.scale = clamp(p.scale, 0.5f, 2.0f); p.speed = clamp(p.speed, 0f, 1f);
            if (p.preset == null) p.preset = GlintPreset.VANILLA.name();
        }
    }

    public void resetAll() { copyFrom(new GlintSettings()); }

    public void copyFrom(GlintSettings o) {
        enabled = o.enabled; preset = o.preset; customEnabled = o.customEnabled;
        customColor1 = o.customColor1; customColor2 = o.customColor2;
        savedColours = new ArrayList<>(o.savedColours);
        savedPresets = new ArrayList<>(o.savedPresets);
        strength = o.strength; opacity = o.opacity; scale = o.scale;
        vanillaSpeed = o.vanillaSpeed; speed = o.speed;
        itemMode = o.itemMode; helmet = o.helmet; chestplate = o.chestplate;
        leggings = o.leggings; boots = o.boots;
        categories = new LinkedHashSet<>(o.categories);
        items = new LinkedHashSet<>(o.items);
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return v < lo ? lo : Math.min(v, hi);
    }
}
