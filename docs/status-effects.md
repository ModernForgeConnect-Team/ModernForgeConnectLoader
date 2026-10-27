# Legacy status effects

MFCL maps the original numeric potion array to native effects by stable names. Vanilla IDs 1–23 map by their Minecraft names; adapters can supply explicit names and texture atlases. Other original potions are discovered from their FML source JAR and potion name. Unknown source identities and unsupported attributes produce mapping diagnostics.

Minecraft 1.20.1 owns effect durations, application/removal, networking, curative items and saved data. Original `isReady`, `performEffect`, instant-effect and attribute-amount callbacks retain their original behavior. Player actors and entity query mirrors expose the mapped native effects. Original potion items convert their effect IDs in both directions.

The TC4 4.2.3.5 fixture contains nine potion instances: `thaumcraft:fluxtaint`, `visexhaust`, `infvisexhaust`, `unhunger`, `warpward`, `deathgaze`, `blurredvision`, `sunscorned`, and `thaumarhia` (each in the `thaumcraft` namespace). Entries described for other TC4 versions are not synthesized. The adapted TC4 utility class retains the Java 17 reflection fix required to expand its original potion array.

TC4's death-gaze and food-cure event callbacks run against native players. Original per-instance curative lists are retained, including effects that milk cannot cure. This does not enable the unrelated random-warp event scheduler. Four original potion screen filters run through native post-processing; their private shader copies are upgraded from GLSL 1.20 to 1.50 and do not replace native shader resources.

Effect sprites use 18×18 cells at `x = index % 8 * 18`, `y = 198 + index / 8 * 18` in the old inventory atlas. The 198-pixel offset applies to TC4's custom atlas as well as the vanilla inventory texture. Generated sprites go into the native `textures/mob_effect/` directory and are regenerated on startup. Restart the game after installing a new MFCL build.

Run the disposable TC4/Baubles fixture with `-PmfclPotionSmokeTest`, a prepared `-PlegacyRuntimeDir`, and an isolated `-PmfclRunDir`. The probe writes `logs/mfcl/potion-smoke.json` and `screenshots/potion-inventory.png`. Verify the report's `status`, individual assertions and rendered screenshot; Gradle's exit code alone is insufficient. The graphical regression includes save/reload and checks that exported sprites contain visible pixels.
