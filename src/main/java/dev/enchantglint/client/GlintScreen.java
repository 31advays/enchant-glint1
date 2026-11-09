package dev.enchantglint.client;

import dev.enchantglint.core.ColorUtil;
import dev.enchantglint.core.GlintPreset;
import dev.enchantglint.core.GlintSettings;
import dev.enchantglint.core.ItemRouter;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom dark-violet GUI for Minecraft 26.3. Panels, sliders, swatches and the colour square are drawn and
 * hit-tested here; the two text fields (hex, item search) are vanilla EditBox widgets so text input works with
 * 26.3's SDL text-input handling without us touching key codes.
 *
 * API sources (not compiled against locally): Fabric docs "Custom Screens" and "Drawing to the GUI" (extractRenderState,
 * GuiGraphicsExtractor.text/fill/fillGradient, Minecraft.gui.setScreen), NeoForge 26.1/26.3 primers (item(), SDL input,
 * left mouse button = 1), real 26.3 Fabric ports (MouseButtonEvent handlers).
 * Still UNVERIFIED(26.3): marked inline.
 */
public final class GlintScreen extends Screen {
    // palette
    private static final int BG = 0xF2120B22, PANEL = 0xFF1B1233, PANEL2 = 0xFF251A45, LINE = 0xFF3A2C66;
    private static final int ACCENT = 0xFFA253FD, ACCENT_DIM = 0xFF5B3499, TEXT = 0xFFEDE6FF, MUTED = 0xFF9C8FC4;

    private enum Tab { PRESETS("Presets"), COLOUR("Colour"), TUNING("Tuning"), ITEMS("Items");
        final String label; Tab(String l) { label = l; } }

    private interface Click { void run(); }
    private record Hit(int x, int y, int w, int h, Click click) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Slider(int x, int y, int w, float min, float max, java.util.function.Supplier<Float> get,
                          java.util.function.Consumer<Float> set) {}

    private final Screen parent;
    private final EnchantGlintClient mod = EnchantGlintClient.instance();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private Slider dragging;
    private boolean draggingHue, draggingSv;
    private Tab tab = Tab.PRESETS;
    private int px, py, pw, ph;

    // colour tab state
    private int editing = 1;                 // which custom colour is being edited (1 or 2)
    private float hue, sat, val;
    private EditBox hexBox;
    private boolean syncingHex;

    // items tab state
    private String search = "";
    private EditBox searchBox;
    private int resultScroll;
    private List<String> results = new ArrayList<>();
    private boolean resultsDirty = true;

    private int loadIndex;
    private ItemStack[] preview;

    public GlintScreen(Screen parent) {
        super(Component.literal("Enchant Glint"));
        this.parent = parent;
        syncColourEditor();
    }

    private GlintSettings s() { return mod.settings(); }

    private void edited() { mod.config().touch(); }           // cheap: rebuild next tick
    private void commit() { mod.changed(); }                  // touch + save to disk

    @Override
    protected void init() {
        pw = Math.min(width - 16, 640);
        ph = Math.min(height - 16, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (preview == null) {
            preview = new ItemStack[] {
                    new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.NETHERITE_BOOTS) };
            // UNVERIFIED(26.3): DataComponents.ENCHANTMENT_GLINT_OVERRIDE (Boolean) via ItemStack.set
            for (ItemStack st : preview) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        hexBox = new EditBox(font, 0, 0, 74, 16, Component.literal("Hex colour"));
        hexBox.setBordered(false);
        hexBox.setMaxLength(7);
        hexBox.setTextColor(TEXT);
        hexBox.setResponder(this::onHexChanged);
        hexBox.setValue(ColorUtil.toHex(current()));
        addWidget(hexBox);

        searchBox = new EditBox(font, 0, 0, 150, 16, Component.literal("Search items"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(40);
        searchBox.setTextColor(TEXT);
        searchBox.setHint(Component.literal("type an item name..."));
        searchBox.setValue(search);
        searchBox.setResponder(t -> { search = t; resultsDirty = true; });
        addWidget(searchBox);
    }

    private void onHexChanged(String text) {
        if (syncingHex) return;
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6) return;
        int c = ColorUtil.parseHex(text);
        if (c >= 0) { setCurrent(c); commit(); }
    }

    private void showTextBox(EditBox box, boolean visible, GuiGraphicsExtractor g, int mx, int my, float delta) {
        box.visible = visible;
        box.active = visible;
        if (!visible) { if (box.isFocused()) box.setFocused(false); return; }
        box.extractRenderState(g, mx, my, delta);
    }

    // ---- rendering -----------------------------------------------------------------------

    // Signature confirmed by Fabric docs (Custom Screens, 26.x).
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        sliders.clear();
        g.fill(0, 0, width, height, 0x99000000);
        round(g, px, py, pw, ph, BG);
        g.text(font, "Enchant Glint", px + 14, py + 10, TEXT, false);
        toggle(g, px + pw - 120, py + 7, 106, "Mod " + (s().enabled ? "ON" : "OFF"), s().enabled, () -> { s().enabled = !s().enabled; commit(); });

        // tabs
        int tx = px + 14;
        for (Tab t : Tab.values()) {
            int w = font.width(t.label) + 20;
            boolean on = t == tab;
            round(g, tx, py + 30, w, 18, on ? ACCENT_DIM : PANEL);
            g.text(font, t.label, tx + 10, py + 35, on ? TEXT : MUTED, false);
            final Tab target = t;
            hits.add(new Hit(tx, py + 30, w, 18, () -> { tab = target; setFocused(null); }));
            tx += w + 4;
        }

        int cx = px + 14, cy = py + 56, cw = pw - 28 - 150, chh = ph - 56 - 44;
        round(g, cx, cy, cw, chh, PANEL);
        switch (tab) {
            case PRESETS -> drawPresets(g, cx + 8, cy + 8, cw - 16);
            case COLOUR -> drawColour(g, cx + 8, cy + 8, cw - 16);
            case TUNING -> drawTuning(g, cx + 8, cy + 8, cw - 16);
            case ITEMS -> drawItems(g, cx + 8, cy + 8, cw - 16, chh - 16);
        }
        drawPreview(g, px + pw - 14 - 140, cy, 140, chh);
        drawFooter(g, px + 14, py + ph - 34);

        for (Slider sl : sliders) drawSlider(g, sl);

        boolean selectedMode = s().itemMode == GlintSettings.ItemMode.SELECTED;
        showTextBox(hexBox, tab == Tab.COLOUR, g, mx, my, delta);
        showTextBox(searchBox, tab == Tab.ITEMS && selectedMode, g, mx, my, delta);
    }

    private void drawPresets(GuiGraphicsExtractor g, int x, int y, int w) {
        GlintPreset[] all = GlintPreset.values();
        int cols = 5, gap = 6, cw = (w - gap * (cols - 1)) / cols, chh = 52;
        for (int i = 0; i < all.length; i++) {
            GlintPreset p = all[i];
            int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (chh + gap);
            boolean sel = !s().customEnabled && s().presetEnum() == p;
            round(g, cx, cy, cw, chh, sel ? ACCENT : LINE);
            swatch(g, cx + 2, cy + 2, cw - 4, chh - 18, p);
            g.text(font, p.label, cx + 4, cy + chh - 12, TEXT, false);
            hits.add(new Hit(cx, cy, cw, chh, () -> { s().preset = p.name(); s().customEnabled = false; commit(); }));
        }
    }

    private void swatch(GuiGraphicsExtractor g, int x, int y, int w, int h, GlintPreset p) {
        if (p.style == GlintPreset.Style.RAINBOW) {
            int seg = 6;
            for (int i = 0; i < seg; i++) {
                int c = 0xFF000000 | ColorUtil.hsvToRgb(i / (float) seg, 0.85f, 1f);
                g.fill(x + w * i / seg, y, x + w * (i + 1) / seg, y + h, c);
            }
        } else {
            g.fillGradient(x, y, x + w, y + h, 0xFF000000 | p.color1, 0xFF000000 | p.color2);
        }
    }

    private void drawColour(GuiGraphicsExtractor g, int x, int y, int w) {
        GlintSettings st = s();
        toggle(g, x, y, 150, "Custom colours " + (st.customEnabled ? "ON" : "OFF"), st.customEnabled,
                () -> { st.customEnabled = !st.customEnabled; commit(); });
        button(g, x + 156, y, 64, "Colour 1", editing == 1, () -> { editing = 1; syncColourEditor(); });
        button(g, x + 224, y, 64, "Colour 2", editing == 2, () -> { editing = 2; syncColourEditor(); });

        // saturation/value square + hue bar
        int sq = 84, sy = y + 28;
        int cells = 14, cs = sq / cells;
        for (int j = 0; j < cells; j++)
            for (int i = 0; i < cells; i++) {
                int c = 0xFF000000 | ColorUtil.hsvToRgb(hue, i / (float) (cells - 1), 1f - j / (float) (cells - 1));
                g.fill(x + i * cs, sy + j * cs, x + (i + 1) * cs, sy + (j + 1) * cs, c);
            }
        int mxp = x + Math.round(sat * (cells - 1) * cs), myp = sy + Math.round((1 - val) * (cells - 1) * cs);
        g.fill(mxp - 2, myp - 2, mxp + cs + 2, myp + cs + 2, 0xFFFFFFFF);
        g.fill(mxp, myp, mxp + cs, myp + cs, 0xFF000000 | current());
        svRect = new int[] { x, sy, cells * cs, cells * cs };

        int hx = x + sq + 10;
        for (int j = 0; j < cells; j++)
            g.fill(hx, sy + j * cs, hx + 12, sy + (j + 1) * cs, 0xFF000000 | ColorUtil.hsvToRgb(j / (float) cells, 1f, 1f));
        int hy = sy + Math.round(hue * (cells - 1) * cs);
        g.fill(hx - 2, hy - 1, hx + 14, hy + cs + 1, 0xFFFFFFFF);
        hueRect = new int[] { hx, sy, 12, cells * cs };

        // RGB sliders + hex
        int rx = hx + 28, rw = Math.max(80, w - (rx - x) - 4);
        int col = current();
        int[] rgb = { ColorUtil.r(col), ColorUtil.g(col), ColorUtil.b(col) };
        String[] names = { "R", "G", "B" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            g.text(font, names[i], rx, sy + i * 22 + 1, MUTED, false);
            sliders.add(new Slider(rx + 12, sy + i * 22, rw - 12, 0, 255, () -> (float) rgb[idx], v -> {
                int[] c = { ColorUtil.r(current()), ColorUtil.g(current()), ColorUtil.b(current()) };
                c[idx] = Math.round(v);
                setCurrent(ColorUtil.rgb(c[0], c[1], c[2]));
            }));
        }
        // hex field
        int fy = sy + 70;
        g.text(font, "HEX", rx, fy + 5, MUTED, false);
        round(g, rx + 26, fy, 80, 16, hexBox.isFocused() ? ACCENT_DIM : PANEL2);
        hexBox.setX(rx + 31);
        hexBox.setY(fy + 4);
        if (!hexBox.isFocused()) {                           // keep the box in step with the picker unless the user is typing
            String h = ColorUtil.toHex(col);
            if (!h.equals(hexBox.getValue())) { syncingHex = true; hexBox.setValue(h); syncingHex = false; }
        }
        g.fill(rx + 112, fy, rx + 128, fy + 16, 0xFF000000 | col);

        // saved colours
        int sy2 = sy + sq + 12;
        button(g, x, sy2, 70, "+ Save colour", false, () -> { st.saveColour(current()); commit(); });
        for (int i = 0; i < st.savedColours.size() && i < GlintSettings.MAX_SAVED_COLOURS; i++) {
            int c = st.savedColours.get(i);
            int sx = x + 76 + (i % 12) * 16, sy3 = sy2 + (i / 12) * 16;
            g.fill(sx, sy3, sx + 14, sy3 + 14, 0xFF000000 | c);
            hits.add(new Hit(sx, sy3, 14, 14, () -> { setCurrent(c); commit(); }));
        }
    }

    private int[] svRect = new int[4], hueRect = new int[4];

    private void drawTuning(GuiGraphicsExtractor g, int x, int y, int w) {
        GlintSettings st = s();
        int sw = Math.min(w - 90, 240);
        labelSlider(g, x, y, sw, "Strength", 0f, 1f, () -> st.strength, v -> st.strength = v, true);
        labelSlider(g, x, y + 30, sw, "Opacity", 0f, 1f, () -> st.opacity, v -> st.opacity = v, true);
        labelSlider(g, x, y + 60, sw, "Scale", 0.5f, 2f, () -> st.scale, v -> st.scale = v, false);
        toggle(g, x, y + 96, 190, "Vanilla animation speed " + (st.vanillaSpeed ? "ON" : "OFF"), st.vanillaSpeed,
                () -> { st.vanillaSpeed = !st.vanillaSpeed; commit(); });
        if (st.vanillaSpeed) {
            g.text(font, "Speed slider unlocks when vanilla speed is OFF", x, y + 126, MUTED, false);
        } else {
            labelSlider(g, x, y + 122, sw, "Speed", 0f, 1f, () -> st.speed, v -> st.speed = v, true);
        }
        g.text(font, "Strength = how much of the plaid shows. Opacity = brightness. Scale = plaid size.", x, y + 154, MUTED, false);
    }

    private void labelSlider(GuiGraphicsExtractor g, int x, int y, int w, String name, float min, float max,
                             java.util.function.Supplier<Float> get, java.util.function.Consumer<Float> set, boolean percent) {
        float v = get.get();
        String val = percent ? Math.round(v * 100) + "%" : String.format(Locale.ROOT, "%.2fx", v);
        g.text(font, name, x, y, TEXT, false);
        g.text(font, val, x + w + 70 - font.width(val), y, MUTED, false);
        sliders.add(new Slider(x, y + 12, w + 70, min, max, get, set));
    }

    private void drawSlider(GuiGraphicsExtractor g, Slider sl) {
        float t = (sl.get.get() - sl.min) / (sl.max - sl.min);
        g.fill(sl.x, sl.y + 3, sl.x + sl.w, sl.y + 7, LINE);
        g.fill(sl.x, sl.y + 3, sl.x + Math.round(sl.w * t), sl.y + 7, ACCENT);
        int kx = sl.x + Math.round(sl.w * t);
        g.fill(kx - 3, sl.y, kx + 3, sl.y + 10, TEXT);
    }

    private void drawItems(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        GlintSettings st = s();
        boolean armourOnly = st.itemMode == GlintSettings.ItemMode.ARMOR_ONLY;
        button(g, x, y, 90, "Armour only", armourOnly, () -> { st.itemMode = GlintSettings.ItemMode.ARMOR_ONLY; commit(); });
        button(g, x + 94, y, 110, "Selected items", !armourOnly, () -> { st.itemMode = GlintSettings.ItemMode.SELECTED; commit(); });

        int ay = y + 24, ax = x;
        String[] names = { "Helmet", "Chestplate", "Leggings", "Boots" };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean on = switch (i) { case 0 -> st.helmet; case 1 -> st.chestplate; case 2 -> st.leggings; default -> st.boots; };
            int bw = font.width(names[i]) + 16;
            toggleSmall(g, ax, ay, bw, names[i], on, () -> {
                switch (idx) { case 0 -> st.helmet = !st.helmet; case 1 -> st.chestplate = !st.chestplate;
                    case 2 -> st.leggings = !st.leggings; default -> st.boots = !st.boots; }
                commit();
            });
            ax += bw + 4;
        }
        if (armourOnly) {
            g.text(font, "Only armour gets the custom glint. Held items stay vanilla.", x, ay + 28, MUTED, false);
            return;
        }
        int cy = ay + 24, cx = x;
        for (ItemRouter.Category c : ItemRouter.Category.values()) {
            if (c == ItemRouter.Category.ARMOR) continue;
            int bw = font.width(c.label) + 14;
            if (cx + bw > x + w) { cx = x; cy += 18; }
            boolean on = st.categories.contains(c.name());
            toggleSmall(g, cx, cy, bw, c.label, on, () -> { if (!st.categories.remove(c.name())) st.categories.add(c.name()); commit(); });
            cx += bw + 4;
        }
        // armour category chip (all armour pieces that are toggled above)
        {
            String l = "All armour";
            int bw = font.width(l) + 14;
            if (cx + bw > x + w) { cx = x; cy += 18; }
            boolean on = st.categories.contains(ItemRouter.Category.ARMOR.name());
            toggleSmall(g, cx, cy, bw, l, on, () -> { String n = ItemRouter.Category.ARMOR.name(); if (!st.categories.remove(n)) st.categories.add(n); commit(); });
        }

        int sy = cy + 24;
        g.text(font, "Search", x, sy + 4, MUTED, false);
        int searchW = Math.min(w - 40, 200);
        round(g, x + 40, sy, searchW, 16, searchBox.isFocused() ? ACCENT_DIM : PANEL2);
        searchBox.setX(x + 45);
        searchBox.setY(sy + 4);
        searchBox.setWidth(searchW - 10);

        if (resultsDirty) rebuildResults();
        int ry = sy + 20, rows = Math.max(1, (y + h - ry) / 14);
        int selX = x + Math.min(w - 40, 200) + 52;
        for (int i = 0; i < rows && i + resultScroll < results.size(); i++) {
            String id = results.get(i + resultScroll);
            boolean on = st.items.contains(id);
            int rowW = Math.min(w, 250);
            g.fill(x, ry + i * 14, x + rowW, ry + i * 14 + 13, on ? ACCENT_DIM : PANEL2);
            g.text(font, shorten(id, rowW - 8), x + 4, ry + i * 14 + 3, TEXT, false);
            hits.add(new Hit(x, ry + i * 14, rowW, 13, () -> { if (!st.items.remove(id)) st.items.add(id); commit(); }));
        }
        g.text(font, st.items.size() + " specific item(s) selected", selX - 40 + 200 < x + w ? x + 256 : x, ry + rows * 14 + 2, MUTED, false);
        if (!st.items.isEmpty()) button(g, x + 256, ry, 70, "Clear items", false, () -> { st.items.clear(); commit(); });
    }

    private void rebuildResults() {
        results.clear();
        String q = search.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        // UNVERIFIED(26.3): BuiltInRegistries.ITEM iteration + Identifier.toString()
        BuiltInRegistries.ITEM.keySet().forEach(k -> {
            String id = k.toString();
            if (q.isEmpty() || id.contains(q)) results.add(id);
        });
        results.sort(String::compareTo);
        resultScroll = 0;
        resultsDirty = false;
    }

    private String shorten(String s, int maxPx) {
        while (s.length() > 3 && font.width(s) > maxPx) s = s.substring(0, s.length() - 2);
        return s;
    }

    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x, y, w, h, PANEL);
        g.text(font, "Live preview", x + 8, y + 6, MUTED, false);
        int cell = 56, gx = x + (w - cell * 2) / 2, gy = y + 24;
        for (int i = 0; i < preview.length; i++) {
            int cx = gx + (i % 2) * cell, cy = gy + (i / 2) * (cell + 4);
            round(g, cx, cy, cell - 4, cell - 4, PANEL2);
            // UNVERIFIED(26.3): pose() is documented as unchanged in 26.3; Matrix3x2fStack pushMatrix/translate/scale/popMatrix assumed
            g.pose().pushMatrix();
            g.pose().translate(cx + 2, cy + 2);
            g.pose().scale(3f, 3f);
            // item(ItemStack, int, int): renderItem was renamed item() in 26.1 (NeoForge primer). Glint is drawn by the game itself.
            g.item(preview[i], 0, 0);
            g.pose().popMatrix();
        }
        g.text(font, "Rendered by the", x + 8, y + h - 24, MUTED, false);
        g.text(font, "game with the glint", x + 8, y + h - 14, MUTED, false);
    }

    private void drawFooter(GuiGraphicsExtractor g, int x, int y) {
        GlintSettings st = s();
        button(g, x, y, 90, "Save preset", false, () -> { st.savePreset("Preset " + (st.savedPresets.size() + 1)); commit(); });
        String loadLabel = st.savedPresets.isEmpty() ? "Load (none)" : "Load: " + st.savedPresets.get(loadIndex % st.savedPresets.size()).name;
        button(g, x + 94, y, 120, loadLabel, false, () -> {
            if (st.savedPresets.isEmpty()) return;
            st.apply(st.savedPresets.get(loadIndex % st.savedPresets.size()));
            loadIndex++; syncColourEditor(); commit();
        });
        button(g, x + 218, y, 80, "Reset all", false, () -> { st.resetAll(); syncColourEditor(); commit(); });
        button(g, px + pw - 14 - 70, y, 70, "Done", false, this::onClose);
    }

    // ---- widgets -------------------------------------------------------------------------

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean on, Click c) {
        round(g, x, y, w, 18, on ? ACCENT_DIM : PANEL2);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 5, on ? TEXT : MUTED, false);
        hits.add(new Hit(x, y, w, 18, c));
    }

    private void toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean on, Click c) { button(g, x, y, w, label, on, c); }

    private void toggleSmall(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean on, Click c) {
        round(g, x, y, w, 15, on ? ACCENT : PANEL2);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 4, on ? 0xFF10091F : MUTED, false);
        hits.add(new Hit(x, y, w, 15, c));
    }

    /** Rounded-corner rectangle using fills only (cheap, no textures). */
    private static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        g.fill(x + 2, y, x + w - 2, y + h, argb);
        g.fill(x, y + 2, x + w, y + h - 2, argb);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb);
    }

    // ---- colour editor helpers ----------------------------------------------------------

    private int current() { return editing == 1 ? s().customColor1 : s().customColor2; }

    private void setCurrent(int rgb) {
        if (editing == 1) s().customColor1 = rgb & 0xFFFFFF; else s().customColor2 = rgb & 0xFFFFFF;
        s().customEnabled = true;
        float[] hsv = ColorUtil.rgbToHsv(current());
        // keep the hue steady when the colour is grey/black so the hue bar does not jump
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0];
        sat = hsv[1]; val = hsv[2];
        edited();
    }

    private void syncColourEditor() {
        float[] hsv = ColorUtil.rgbToHsv(current());
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
    }

    private void applyHsv() {
        s().customEnabled = true;
        int c = ColorUtil.hsvToRgb(hue, sat, val);
        if (editing == 1) s().customColor1 = c; else s().customColor2 = c;
        edited();
    }

    // ---- input ---------------------------------------------------------------------------

    // MouseButtonEvent handlers confirmed in 26.3 Fabric ports. 26.3 (SDL): left button is 1, use the InputConstants constant.
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(e, doubleClick);
        if (super.mouseClicked(e, doubleClick)) return true;     // lets the EditBox widgets take focus
        setFocused(null);
        double mx = e.x(), my = e.y();
        for (Slider sl : sliders) {
            if (mx >= sl.x - 3 && mx <= sl.x + sl.w + 3 && my >= sl.y - 2 && my <= sl.y + 12) {
                dragging = sl; dragTo(mx); return true;
            }
        }
        if (tab == Tab.COLOUR) {
            if (inRect(svRect, mx, my)) { draggingSv = true; svTo(mx, my); return true; }
            if (inRect(hueRect, mx, my)) { draggingHue = true; hueTo(my); return true; }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) { h.click.run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) { dragTo(e.x()); return true; }
        if (draggingSv) { svTo(e.x(), e.y()); return true; }
        if (draggingHue) { hueTo(e.y()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    // UNVERIFIED(26.3): mouseReleased(MouseButtonEvent)
    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null || draggingSv || draggingHue) commit();   // one disk write per drag
        dragging = null; draggingSv = draggingHue = false;
        return super.mouseReleased(e);
    }

    private void dragTo(double mx) {
        Slider sl = dragging;
        float t = (float) Math.max(0, Math.min(1, (mx - sl.x) / sl.w));
        float v = sl.min + t * (sl.max - sl.min);
        sl.set.accept(v);
        edited();
    }

    private void svTo(double mx, double my) {
        sat = (float) Math.max(0, Math.min(1, (mx - svRect[0]) / (svRect[2] - 1)));
        val = 1f - (float) Math.max(0, Math.min(1, (my - svRect[1]) / (svRect[3] - 1)));
        applyHsv();
    }

    private void hueTo(double my) {
        hue = (float) Math.max(0, Math.min(0.999, (my - hueRect[1]) / hueRect[3]));
        applyHsv();
    }

    private static boolean inRect(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // UNVERIFIED(26.3): mouseScrolled(double, double, double, double)
    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == Tab.ITEMS && !results.isEmpty()) {
            resultScroll = Math.max(0, Math.min(results.size() - 1, resultScroll - (int) Math.signum(sy) * 3));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        mod.changed();
        // Confirmed (Fabric docs): screens moved from Minecraft to Minecraft.gui in 26.2
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
