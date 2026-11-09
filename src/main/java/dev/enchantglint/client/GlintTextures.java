package dev.enchantglint.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.enchantglint.core.GlintSettings;
import dev.enchantglint.core.ItemRouter;
import dev.enchantglint.core.PlaidTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Replaces vanilla's two glint textures by registering generated textures under the same ids.
 * This is the whole rendering mechanism: the game keeps rendering glint exactly as before
 * (same RenderTypes, same scroll, same shaders), so Sodium and other renderers are unaffected.
 *
 * Work done: only when settings change (or after a resource reload replaced our texture).
 * Per-frame cost: zero. Per-tick cost: two reference comparisons.
 */
public final class GlintTextures {
    // UNVERIFIED(26.3): Identifier.withDefaultNamespace (ResourceLocation was renamed Identifier in 26.1)
    private static final Identifier ITEM_ID = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR_ID = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    private final Slot item = new Slot(ITEM_ID, Calibration.ITEM_CYCLE_MULT, Calibration.ITEM_GAIN);
    private final Slot armor = new Slot(ARMOR_ID, Calibration.ARMOR_CYCLE_MULT, Calibration.ARMOR_GAIN);
    private int appliedRevision = -1;

    /** Call every client tick. */
    public void tick(int configRevision, GlintSettings s) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        boolean changed = configRevision != appliedRevision;
        if (changed) appliedRevision = configRevision;
        item.sync(tm, s, changed, ItemRouter.itemTextureActive(s));
        armor.sync(tm, s, changed, ItemRouter.armourTextureActive(s));
    }

    /** Forget our textures (e.g. when the mod is disabled) so vanilla's are used again. */
    public void restoreVanilla() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        item.release(tm);
        armor.release(tm);
        appliedRevision = -1;
    }

    private static final class Slot {
        final Identifier id;
        final float cycleMult, gain;
        PlaidTexture.Params applied;
        DynamicTexture texture;
        boolean wasActive;

        Slot(Identifier id, float cycleMult, float gain) { this.id = id; this.cycleMult = cycleMult; this.gain = gain; }

        void sync(TextureManager tm, GlintSettings s, boolean changed, boolean active) {
            if (!active) {
                if (wasActive) release(tm);
                return;
            }
            PlaidTexture.Params want = new PlaidTexture.Params(
                    PlaidTexture.cyclesForScale(s.scale, cycleMult),
                    s.effectiveColor1(), s.effectiveColor2(), s.effectiveStyle(), s.effectiveAxis(),
                    s.strength, s.opacity, gain * s.effectiveIntensity());
            // UNVERIFIED(26.3): TextureManager.getTexture(Identifier) return type / behaviour for a missing id
            AbstractTexture current = tm.getTexture(id);
            boolean replacedByReload = texture != null && current != texture;
            if (!wasActive || replacedByReload || !want.equals(applied)) {
                upload(tm, want);
            }
            wasActive = true;
        }

        void upload(TextureManager tm, PlaidTexture.Params p) {
            int[] argb = PlaidTexture.generate(p);
            int n = PlaidTexture.SIZE;
            // UNVERIFIED(26.3): NativeImage constructor (Format, w, h, zero) and setPixel(x, y, ARGB int)
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, false);
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++)
                    img.setPixel(x, y, argb[y * n + x]);
            // UNVERIFIED(26.3): DynamicTexture(Supplier<String>, NativeImage); 26.2 reworked GPU texture APIs
            DynamicTexture dt = new DynamicTexture(() -> "enchantglint:" + id.getPath(), img);
            // UNVERIFIED(26.3): TextureManager.register(Identifier, AbstractTexture) closes the previous texture
            tm.register(id, dt);
            texture = dt;
            applied = p;
            System.out.println("[EnchantGlint] registered generated glint texture for " + id + " (cycles=" + p.cycles() + ", style=" + p.style() + ")");
        }

        void release(TextureManager tm) {
            if (texture != null) {
                // UNVERIFIED(26.3): release(Identifier) drops our texture; the next lookup reloads vanilla's from resources
                tm.release(id);
                texture = null;
            }
            applied = null;
            wasActive = false;
        }
    }
}
