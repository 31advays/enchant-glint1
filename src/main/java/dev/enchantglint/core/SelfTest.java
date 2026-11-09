package dev.enchantglint.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Plain-Java checks of the Minecraft-independent core. Run: java dev.enchantglint.core.SelfTest */
public final class SelfTest {
    private static int failures = 0;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        check(GlintPreset.values().length == 15, "15 presets");

        check(ColorUtil.parseHex("#a253fd") == 0xA253FD, "hex parse 6");
        check(ColorUtil.parseHex("f0a") == 0xFF00AA, "hex parse 3");
        check(ColorUtil.parseHex("zzz") == -1 && ColorUtil.parseHex(null) == -1, "hex parse invalid");
        check(ColorUtil.toHex(0x0A0B0C).equals("#0A0B0C"), "hex format");
        for (int c : new int[] { 0xA253FD, 0xFF0000, 0x00FF00, 0x123456, 0xFFFFFF, 0x000000 }) {
            float[] h = ColorUtil.rgbToHsv(c);
            int back = ColorUtil.hsvToRgb(h[0], h[1], h[2]);
            check(Math.abs(ColorUtil.r(c) - ColorUtil.r(back)) <= 1 && Math.abs(ColorUtil.g(c) - ColorUtil.g(back)) <= 1
                    && Math.abs(ColorUtil.b(c) - ColorUtil.b(back)) <= 1, "hsv round trip " + ColorUtil.toHex(c));
        }

        // texture: determinism, seamlessness, independence of controls
        PlaidTexture.Params base = new PlaidTexture.Params(2, 0xA253FD, 0x5EC8FF, GlintPreset.Style.GRADIENT, 0, 0.8f, 1f, 1.15f);
        int[] a = PlaidTexture.generate(base), b = PlaidTexture.generate(base);
        check(java.util.Arrays.equals(a, b), "texture deterministic");
        check(a.length == PlaidTexture.SIZE * PlaidTexture.SIZE, "texture size");

        int[] half = PlaidTexture.generate(new PlaidTexture.Params(2, 0xA253FD, 0x5EC8FF, GlintPreset.Style.GRADIENT, 0, 0.8f, 0.5f, 1.15f));
        check(sum(half) < sum(a) * 0.6, "opacity darkens uniformly");
        int[] weak = PlaidTexture.generate(new PlaidTexture.Params(2, 0xA253FD, 0x5EC8FF, GlintPreset.Style.GRADIENT, 0, 0.1f, 1f, 1.15f));
        int[] strong = PlaidTexture.generate(new PlaidTexture.Params(2, 0xA253FD, 0x5EC8FF, GlintPreset.Style.GRADIENT, 0, 1f, 1f, 1.15f));
        check(coverage(weak) < coverage(strong), "strength increases coverage");
        check(Math.abs(maxChannel(weak) - maxChannel(strong)) < 25, "strength keeps peak brightness (independent of opacity)");
        check(PlaidTexture.cyclesForScale(0.5f, 1f) < PlaidTexture.cyclesForScale(2f, 1f), "scale changes cycles");
        // seamless: wrap-around neighbour difference is similar to interior neighbour difference
        int n = PlaidTexture.SIZE;
        long wrap = 0, inner = 0;
        for (int y = 0; y < n; y++) {
            wrap += Math.abs(lum(a[y * n] ) - lum(a[y * n + n - 1]));
            inner += Math.abs(lum(a[y * n + n / 2]) - lum(a[y * n + n / 2 - 1]));
        }
        check(wrap <= inner * 4 + n * 20, "texture tiles seamlessly (wrap seam ~ interior step)");
        for (GlintPreset p : GlintPreset.values()) {
            int[] t = PlaidTexture.generate(new PlaidTexture.Params(2, p.color1, p.color2, p.style, p.axis, 0.8f, 1f, 1.15f * p.intensity));
            check(maxChannel(t) > 100, "preset visible: " + p.label);
        }

        // router
        GlintSettings s = new GlintSettings();
        check(ItemRouter.matches(s, "minecraft:diamond_chestplate"), "armour-only matches armour");
        check(!ItemRouter.matches(s, "minecraft:diamond_sword"), "armour-only ignores sword");
        s.helmet = false;
        check(!ItemRouter.matches(s, "minecraft:netherite_helmet"), "armour piece toggle");
        s.itemMode = GlintSettings.ItemMode.SELECTED;
        s.categories.add(ItemRouter.Category.SWORDS.name());
        s.items.add("minecraft:golden_apple");
        check(ItemRouter.matches(s, "minecraft:netherite_sword"), "selected category sword");
        check(ItemRouter.matches(s, "minecraft:golden_apple"), "selected specific item");
        check(!ItemRouter.matches(s, "minecraft:diamond_axe"), "unselected axe ignored");
        check(ItemRouter.categoryOf("minecraft:diamond_pickaxe") == ItemRouter.Category.PICKAXES, "pickaxe not axe");
        check(ItemRouter.itemTextureActive(s) && !ItemRouter.armourTextureActive(s), "texture gating selected non-armour");

        // config round trip + corruption
        Path dir = Files.createTempDirectory("eg");
        Path f = dir.resolve("cfg.json");
        GlintConfig cfg = new GlintConfig(f);
        cfg.load();
        GlintSettings g = cfg.get();
        g.preset = GlintPreset.COTTON_CANDY.name(); g.customEnabled = true; g.customColor1 = 0x112233;
        g.strength = 5f; g.scale = -3f; g.vanillaSpeed = false; g.speed = 0.7f;
        g.saveColour(0xABCDEF); g.savePreset("mine"); g.items.add("minecraft:bow");
        cfg.save();
        GlintConfig cfg2 = new GlintConfig(f);
        cfg2.load();
        GlintSettings h = cfg2.get();
        check(h.preset.equals("COTTON_CANDY") && h.customColor1 == 0x112233 && h.customEnabled, "config round trip basics");
        check(h.strength == 1f && h.scale == 0.5f, "config clamps");
        check(!h.vanillaSpeed && h.speed == 0.7f, "config speed settings");
        check(h.savedColours.contains(0xABCDEF) && h.savedPresets.size() == 1 && h.items.contains("minecraft:bow"), "saved colours/presets/items persist");
        h.apply(h.savedPresets.get(0));
        check(h.preset.equals("COTTON_CANDY"), "apply saved preset");
        Files.writeString(f, "{ not json");
        GlintConfig cfg3 = new GlintConfig(f);
        cfg3.load();
        check(cfg3.get().preset.equals("VANILLA") && Files.exists(dir.resolve("cfg.json.broken")), "corrupt config recovered");
        cfg3.get().resetAll();
        check(cfg3.get().strength == 0.8f, "reset all");

        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILED");
        if (failures != 0) System.exit(1);
    }

    private static long sum(int[] px) { long s = 0; for (int p : px) s += lum(p); return s; }
    private static int lum(int p) { return ColorUtil.r(p) + ColorUtil.g(p) + ColorUtil.b(p); }
    private static int maxChannel(int[] px) { int m = 0; for (int p : px) m = Math.max(m, Math.max(ColorUtil.r(p), Math.max(ColorUtil.g(p), ColorUtil.b(p)))); return m; }
    private static double coverage(int[] px) { int c = 0; for (int p : px) if (lum(p) > 60) c++; return (double) c / px.length; }
}
