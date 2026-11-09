package dev.enchantglint.core;

import java.util.Locale;

/** Decides which items a given settings object applies to. Works on item id strings only. */
public final class ItemRouter {
    private ItemRouter() {}

    public enum Category {
        SWORDS("Swords"), AXES("Axes"), PICKAXES("Pickaxes"), SHOVELS("Shovels"), HOES("Hoes"),
        BOWS("Bows"), CROSSBOWS("Crossbows"), TRIDENTS("Tridents"), SHIELDS("Shields"),
        ARMOR("Armour"), OTHERS("Others");
        public final String label;
        Category(String l) { label = l; }
    }

    public enum ArmorSlot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    private static String path(String id) {
        int i = id.indexOf(':');
        return (i >= 0 ? id.substring(i + 1) : id).toLowerCase(Locale.ROOT);
    }

    public static ArmorSlot armorSlot(String id) {
        String p = path(id);
        if (p.endsWith("_helmet")) return ArmorSlot.HELMET;
        if (p.endsWith("_chestplate")) return ArmorSlot.CHESTPLATE;
        if (p.endsWith("_leggings")) return ArmorSlot.LEGGINGS;
        if (p.endsWith("_boots")) return ArmorSlot.BOOTS;
        return null;
    }

    public static Category categoryOf(String id) {
        String p = path(id);
        if (armorSlot(id) != null) return Category.ARMOR;
        if (p.endsWith("_sword")) return Category.SWORDS;
        if (p.endsWith("_pickaxe")) return Category.PICKAXES;
        if (p.endsWith("_axe")) return Category.AXES;
        if (p.endsWith("_shovel")) return Category.SHOVELS;
        if (p.endsWith("_hoe")) return Category.HOES;
        if (p.equals("bow")) return Category.BOWS;
        if (p.equals("crossbow")) return Category.CROSSBOWS;
        if (p.equals("trident")) return Category.TRIDENTS;
        if (p.equals("shield")) return Category.SHIELDS;
        return Category.OTHERS;
    }

    public static boolean slotEnabled(GlintSettings s, ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> s.helmet;
            case CHESTPLATE -> s.chestplate;
            case LEGGINGS -> s.leggings;
            case BOOTS -> s.boots;
        };
    }

    /** Should this specific item get the custom glint? */
    public static boolean matches(GlintSettings s, String itemId) {
        if (!s.enabled) return false;
        ArmorSlot slot = armorSlot(itemId);
        if (s.itemMode == GlintSettings.ItemMode.ARMOR_ONLY) {
            return slot != null && slotEnabled(s, slot);
        }
        if (s.items.contains(itemId)) return true;
        Category c = categoryOf(itemId);
        if (c == Category.ARMOR) return s.categories.contains(c.name()) && slotEnabled(s, slot);
        return s.categories.contains(c.name());
    }

    /**
     * Coarse switch used by the texture layer: vanilla keeps two glint textures (armour and
     * held/GUI items), so we can enforce selection at that granularity without per-item hooks.
     */
    public static boolean armourTextureActive(GlintSettings s) {
        if (!s.enabled) return false;
        if (s.itemMode == GlintSettings.ItemMode.ARMOR_ONLY)
            return s.helmet || s.chestplate || s.leggings || s.boots;
        if (s.categories.contains(Category.ARMOR.name())) return true;
        for (String id : s.items) if (armorSlot(id) != null) return true;
        return false;
    }

    public static boolean itemTextureActive(GlintSettings s) {
        if (!s.enabled || s.itemMode == GlintSettings.ItemMode.ARMOR_ONLY) return false;
        for (String c : s.categories) if (!c.equals(Category.ARMOR.name())) return true;
        for (String id : s.items) if (armorSlot(id) == null) return true;
        return false;
    }
}
