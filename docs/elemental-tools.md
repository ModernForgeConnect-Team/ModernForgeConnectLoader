# TC4 elemental tool right clicks

Reference: original Thaumcraft 4.2.3.5, `ItemElementalSword`, `ItemElementalAxe` and `ItemElementalPickaxe`.

Sword and axe already entered native held use and called original `onUsingTick`, including their particles. Changes to queried entities' velocity were discarded because only tile/entity ticks opened an entity-query capture. Changes to the actor's own motion/fall distance also had no held-item write-back. Held-use ticks now capture and commit queried entity motion, clear their query scope even on failure, and push the original actor's changed body state after successful execution. The server's placement transaction stages queried entity effects until commit. Inventory synchronization continues to retain the live use-stack reference across durability changes.

This preserves the original sword lift, fall reduction, nearby entity repulsion, sound and durability cadence. The original axe attraction formula and speed cap now reach authoritative dropped item entities. The implementation invokes the actual 4.2.3.5 method rather than recreating its attraction based on descriptions from other releases.

While the sword is in use, the adapter also resets the real server connection's floating counter, matching TC4's `Utils.resetFloatCounter`. Resetting only the old actor's dummy connection would leave dedicated-server flight checks counting the intended levitation as unsupported flight.

Entity query exclusion and type filtering now happen before resolving mirrors. Previously an "entities except this player" query refreshed the player's persistent actor before removing it from the returned list, erasing the lift computed earlier in the same callback. Motion capture also includes hosted original entities, so they receive pushes alongside ordinary native entities.

The pickaxe already invoked original `startScan` on the client and charged five durability on the server, but TC4's old world-render event did not run. A client adapter now calls original `showScannedBlocks` inside a geometry capture and replays the result from the modern world-render event. Original ore detection, scan center/range, aspect-based intensity, textures, animation and expiry remain in TC4. Depth-test state is now preserved separately from depth writes, allowing the original prospecting markers to show through surrounding blocks. Ordinary batches retain depth testing by default.

## Acceptance

Use an isolated directory containing TC4 and Baubles under `mods/1.7.10`:

```powershell
.\gradlew.bat --project-cache-dir build/elemental-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/elemental-validation' `
  runClient '-PmfclRunDir=build/elemental-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclElementalToolsSmokeTest --console=plain
```

Inspect `logs/mfcl/elemental-tools-smoke.json` separately from the Gradle exit code. The disposable integrated-world probe uses native item callbacks to verify server/client sword lift, fall reduction and wear, the real connection's floating-counter reset, actual entity repulsion, actual item attraction and speed cap, release and item conservation, the original diamond-ore scan, captured marker geometry without depth tests/writes, persistence after switching to a modern-only item, expiry and exactly five server durability cost. It also holds right click through ordinary client/server ticks and checks actual ascent and wear. The fire-focus host probe is a regression for the shared held-use and entity-query path.
