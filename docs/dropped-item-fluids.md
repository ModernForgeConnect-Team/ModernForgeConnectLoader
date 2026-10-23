# Dropped items and TC4 bath salts

Reference: original Thaumcraft 4.2.3.5, Forge 1.7.10 and host Forge 1.20.1 / 47.4.28.

The [Purifying Fluid entry](https://www.mcmod.cn/item/30181.html) describes making purifying fluid by throwing bath salts into water. The original JAR implements this through item expiration: `ItemBathSalts.getEntityLifespan` returns 200 ticks, and `EventHandlerEntity.itemExpire` handles the old `ItemExpireEvent`. At expiration it floors the dropped entity's position and replaces the block only if it is original still water with metadata 0. This is a roughly ten-second delay at 20 TPS, rather than an immediate collision recipe. The original expiration consumes the entire dropped stack, including stacked salts; dry salts also expire after 200 ticks without a conversion.

MFCL previously used the host default item lifespan and did not forward item expiration to the old event bus. `HostDroppedItems` now supplies original lifespans for mirrored items, armor and block items, and forwards native expiration events for representable stacks. `LegacyDroppedItems` builds an original item entity snapshot with the native age and lifespan and posts the original Forge event. World changes run in the normal host transaction; cancellation/extra life, stack changes, removal and motion return to the native entity. Native aging, physics, pickup and persistence continue to own the entity. Original mod JARs are unchanged.

## Acceptance

Use a disposable game directory containing the original TC4 and Baubles JARs under `mods/1.7.10`, with a prepared runtime:

```powershell
.\gradlew.bat --project-cache-dir build/dropped-item-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/dropped-item-validation' `
  runClient '-PmfclRunDir=build/dropped-item-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclDroppedItemFluidSmokeTest --console=plain
```

The probe creates an enclosed water fixture in its own world and writes `logs/mfcl/dropped-item-fluid-smoke.json`. It checks native lifespans, no conversion before expiration, natural source-water conversion at 200 ticks, dry-salt expiration, unchanged ordinary items, stacked-salt behavior, and rejection of flowing water through a native expiration tick. It then stops its client. It does not claim coverage of all dropped-item update callbacks, all liquid interactions, or custom dropped entity classes.
