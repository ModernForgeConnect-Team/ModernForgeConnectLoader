# TC4 enchantments

The reference version is Thaumcraft 4.2.3.5. Its configured `Config.enchRepair` and `Config.enchHaste` objects are registered as native `thaumcraft:repair` (I–II) and `thaumcraft:haste` (I–III). Original weights, costs, eligibility, book availability and compatibility are queried from the old objects. Numeric IDs come from the running old registry, including user configuration; the bridge does not hardcode TC4's IDs.

Modern enchantment-table selection, `/enchant`, enchanted books and anvils use these native registrations. Original numeric `ench`/`StoredEnchantments` lists convert through binary NBT. Unknown old IDs remain in original data, while modern-only enchantments remain host-owned. Saved numeric stacks migrate once; removing a mapped enchantment with a grindstone does not restore it from the preserved source tag. Native vanilla enchanted boots can be mirrored without losing their levels.

Repair executes TC4's original `EventHandlerEntity.doRepair` on the server every 40 ticks for damaged `IRepairable` inventory/equipment, skipping creative players and unworn harnesses as the original handler does. The original aspect reduction, wand selection, vis payment and extended repair hooks determine the result. Haste calls the original movement routine for the local player's enchanted boots, including airborne and water reductions. Equipped harness code reads the same numeric haste NBT through the armor bridge. If an addon already forwards the old living-update event, the targeted callbacks defer to that event to avoid double effects.

TC4 4.2.3.5 does not register the obsolete Frugal/Potency/WandFortune enchantment classes. Frugal, Potency and Treasure are focus upgrades in this version; creating duplicate native enchantments for those classes would not drive the current focus implementation.

Native Forge event listeners must be registered with the modern context class loader. Registering `PlayerTickEvent` while the private 1.7.10 loader is active makes Forge resolve the modern parent through the old runtime. That interrupts TC4 adapter initialization before biome/dimension registration and leaves an existing world unable to resolve `thaumcraft:outer_lands`. The enchantment callback switches the context loader for listener registration and restores it afterward.

## Acceptance

Place TC4 4.2.3.5 and Baubles 1.0.1.10 in a disposable directory's `mods/1.7.10`, then run:

```powershell
.\gradlew.bat --project-cache-dir build/enchantment-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/enchantment-validation' `
  runClient '-PmfclRunDir=build/enchantment-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclEnchantmentSmokeTest --console=plain
```

Read `logs/mfcl/enchantment-smoke.json` in the disposable game directory. The probe exercises registry/level/rule checks, actual native table selection, command execution, anvil and grindstone outputs, numeric and saved NBT, no-vis and paid repair, worn armor, creative exclusion, and local haste motion. It also checks the Outer Lands dimension type and reloads its saved world with an enchanted item.
