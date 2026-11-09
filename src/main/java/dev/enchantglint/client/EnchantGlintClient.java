package dev.enchantglint.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.enchantglint.core.GlintConfig;
import dev.enchantglint.core.GlintSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class EnchantGlintClient implements ClientModInitializer {
    public static final String MOD_ID = "enchantglint";
    private static EnchantGlintClient instance;

    private GlintConfig config;
    private final GlintTextures textures = new GlintTextures();
    private KeyMapping openKey;

    public static EnchantGlintClient instance() { return instance; }
    public GlintSettings settings() { return config.get(); }
    public GlintConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = new GlintConfig(FabricLoader.getInstance().getConfigDir().resolve("enchantglint.json"));
        config.load();

        // 26.3 moved from GLFW to SDL: org.lwjgl.glfw is no longer on the classpath, InputConstants.Type.KEYSYM became
        // KEYBOARD, and key codes are the remapped InputConstants.KEY_* values (NeoForge 26.3 primer; 26.3 Fabric ports).
        // KeyMapping.Category.register(Identifier) and KeyMappingHelper.registerKeyMapping(...) are from the Fabric docs.
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.enchantglint.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            textures.tick(config.revision(), config.get());
            while (openKey.consumeClick()) {
                mc.gui.setScreen(new GlintScreen(mc.gui.screen()));   // Minecraft#setScreen/screen moved to Minecraft.gui in 26.2
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> config.save());
    }

    /** Called by the GUI after any edit. Saves immediately; texture rebuild happens next tick. */
    public void changed() {
        config.touch();
        config.save();
    }
}
