# Arcane Bore native pickaxes

TC4 4.2.3.5 checks the old `ItemPickaxe` class in its container and tile. Modern-only pickaxes now use a typed old proxy, with the full native stack (item identity, NBT and Forge capabilities) stored in its payload. Pickaxe subclasses, Minecraft/Forge pickaxe tags and Forge's `PICKAXE_DIG` action are recognized. Existing original mod items and the five old vanilla pickaxes keep their original mapping.

The TC4 adapter changes the bore's availability check and wear call only. Native wear invokes Forge's item damage hook before vanilla wear, preserving modern tool modifiers and Unbreaking. A consumed ordinary tool is cleared normally. Tinkers' `tic_broken` state stops the bore while retaining the tool; repairing it allows mining again. Broken picks can still be inserted and removed. Full native durability stays in the payload because old stack serialization stores damage as a signed short. Native operations restore the host context class loader before running Forge callbacks.

This targets modern Forge 1.20.1 tools, including modern Tinkers' Construct. It does not add support for old Tinkers' tools. TC4 retains its original excavation, focus and drop logic; this is not forwarding every modern block-breaking modifier to the bore.

## Verification

The dedicated-server probe uses real TC4/Baubles and optionally real modern Tinkers/Mantle:

```powershell
.\gradlew.bat --project-cache-dir build/bore-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/bore-validation' `
  runServer '-PmfclRunDir=build/bore-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  '-PmfclServerSmokeTest=bore' `
  '-PtinkersTestVersion=3.12.1.231' '-PmantleTestVersion=1.11.117'
```

Prepare TC4 4.2.3.5/Baubles 1.0.1.10 under the disposable game's `mods/1.7.10/`, and accept its EULA. Optional test dependencies are downloaded/remapped by Gradle; they are not bundled with MFCL.

Read `logs/mfcl/bore-smoke.json`, not just Gradle's exit status. The probe covers original slot acceptance, native enchantment levels, native wear and payload preservation, GUI stack mapping, tile NBT save/load, shift transfers, last durability, ordinary breakage, Unbreakable, original vanilla wear, axe rejection, real Tinkers break/stop/repair and persistence of the broken state. It exercises the original bore gate and wear method; it does not automate graphical clicks or a full excavation installation.

Tinkers behavior follows its [native damage hooks](https://github.com/SlimeKnights/TinkersConstruct/blob/1.20.1/src/main/java/slimeknights/tconstruct/library/tools/helper/ToolDamageUtil.java) and [modifiable item implementation](https://github.com/SlimeKnights/TinkersConstruct/blob/1.20.1/src/main/java/slimeknights/tconstruct/library/tools/item/ModifiableItem.java).
