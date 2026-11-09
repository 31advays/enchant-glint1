# Enchant Glint (Fabric, client-side, Minecraft Java 26.3)

Custom enchantment glint: 15 presets, custom RGB/HEX/HSV colours, strength / opacity / scale / speed,
a Vanilla Animation Speed toggle, armour and item selection, and a dark-violet settings GUI.

> **Status: NOT YET COMPILED against Minecraft 26.3. NOT tested in game.**
> The pure-Java core is tested (see below). Everything that touches Minecraft is written from
> memory of 1.21.x and research, and is tagged `UNVERIFIED(26.3)` in the source. Expect a few
> compile errors on the first CI run; they should be small renames.

## Build (GitHub Actions)

1. Create a repo, upload the contents of this folder (keep `.github/`), push.
2. Actions tab -> `build` -> Run workflow (it also runs on every push).
3. Two jobs: `core-selftest` (pure Java, should be green immediately) and `build` (Gradle + Loom).
4. Green: download the `enchant-glint-jar` artifact (use the jar that is not `-sources`).
5. Red: open the failed step, copy the log, and send it back.

No wrapper jar is included; CI uses `gradle/actions/setup-gradle` with Gradle 9.6.0.
For local builds install Gradle 9.6 + JDK 25 and run `gradle build`.

## Install

Fabric Loader >= 0.19.5, Fabric API for 26.3, Java 25. Drop the jar in `mods/`. Client only.
In game press **Right Shift** (rebindable under Controls -> Enchant Glint) to open the settings.
Config file: `config/enchantglint.json`.

## How the rendering works

Vanilla draws glint with two textures (`enchanted_glint_item.png` for held/GUI/dropped items,
`enchanted_glint_armor.png` for worn armour). On each settings change the mod generates an
original woven-plaid texture (no third-party texture data) and registers it under those same ids.
The game's own glint code, scroll motion and shaders run unchanged, so there is no per-frame work
from the mod and nothing that should conflict with Sodium.

- **Strength**: how much of the plaid shows (coverage). **Opacity**: brightness. **Scale**: plaid size
  (integer repeats per tile so the tile stays seamless; the slider snaps in steps). Independent.
- **Vanilla Animation Speed ON**: the mod does nothing to speed. **OFF**: a small mixin makes the
  game read the Speed slider value instead of its own glint speed option.
- Armour vs held items use separate textures with separate calibration (`client/Calibration.java`).

## Known limitations (be aware)

- **Item selection is enforced per texture, not per item.** "Armour only" customises armour and leaves held
  items vanilla. In "Selected items" mode the armour texture is customised if any armour is selected and the
  item texture if any non-armour item or category is selected. Choosing *one* sword changes the glint on *all*
  glinted held items, and the armour piece toggles do not yet filter individual pieces at render time.
  True per-item routing needs a render hook whose 26.3 signatures are not verified yet; `core/ItemRouter` already
  has the per-item logic (tested) ready to be wired in.
- Presets differ by colour and colour flow only (Rainbow bands, diagonal vs vertical gradients, Minimal is dimmer).
  The additive glint blend cannot darken, so Shadow is a dim violet glint, not a dark overlay.
- Scale/armour calibration constants are guesses until seen in game.

## Verified vs unverified

| Part | Status |
|---|---|
| Plaid generator: determinism, seamless tiling, strength/opacity independence, 15 presets visible | Tested (JDK 21, 48 checks) and eyeballed as PNG |
| Hex/HSV maths, config round trip, clamping, corrupt-file recovery, item router | Tested (same suite) |
| Gradle / Loom plugin id, versions, `implementation` deps | UNVERIFIED |
| `NativeImage`, `DynamicTexture`, `TextureManager.register/release/getTexture` | UNVERIFIED |
| Registering under vanilla glint ids actually replaces the glint in 26.3 | UNVERIFIED (core assumption) |
| Texture survives resource reload (identity check each tick) | UNVERIFIED |
| `OptionInstance.get` mixin / `Options.glintSpeed()` | UNVERIFIED |
| Keybind registration (`KeyMapping.Category`, `KeyMappingHelper`) | UNVERIFIED |
| GUI: `render`, input event records, `pose()` stack, `renderItem`, `drawString` | UNVERIFIED |
| Sodium compatibility | UNVERIFIED (design avoids touching rendering code, not tested) |
| Performance | UNVERIFIED (design: zero per-frame work; rebuild only on change) |

## Layout

```
src/main/java/dev/enchantglint/
  core/    pure Java: GlintPreset, PlaidTexture, GlintSettings, GlintConfig, ItemRouter, ColorUtil, SelfTest
  client/  EnchantGlintClient, GlintTextures, GlintScreen, Calibration
  mixin/   OptionInstanceMixin (non-required)
```
