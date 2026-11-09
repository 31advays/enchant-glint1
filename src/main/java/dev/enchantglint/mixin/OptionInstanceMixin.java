package dev.enchantglint.mixin;

import dev.enchantglint.client.EnchantGlintClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Animation Speed toggle.
 * When "Vanilla Animation Speed" is ON we do nothing at all (vanilla's own option value is used).
 * When OFF, reads of the glint speed option return our manual slider value instead, so the
 * game's own scroll code runs unchanged - we never touch the texture matrix.
 *
 * UNVERIFIED(26.3): target "get" on OptionInstance, and Options#glintSpeed() accessor name.
 * Mixin is non-required (see enchantglint.mixins.json): if it fails to apply, the mod still loads and
 * only the manual speed slider stops working.
 */
@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void enchantglint$overrideGlintSpeed(CallbackInfoReturnable<Object> cir) {
        EnchantGlintClient mod = EnchantGlintClient.instance();
        if (mod == null || mod.settings().vanillaSpeed || !mod.settings().enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if ((Object) this == mc.options.glintSpeed()) {
            cir.setReturnValue((double) mod.settings().speed);
        }
    }
}
