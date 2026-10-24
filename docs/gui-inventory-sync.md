# Original GUI inventory and close synchronization

Extra Utilities' item transfer node uses the common `LegacyDeviceMenu` bridge. Its GUI does not opt into local click prediction. Previously, native player-inventory reconciliation was registered only for predicted containers, so authoritative node-menu slots could update while the client's actual backpack/hotbar retained their old values.

Every original GUI now binds its player-inventory slot map independently of prediction. Both complete menu snapshots and individual slot updates reconcile those native inventory slots. Containers still opt into prediction individually; the original server container continues to decide each click.

This is shared container infrastructure, not an Extra Utilities class-name exception. Captured FML block/portable GUI requests, original `IInventory` chest requests, and registered device providers all open the same native `LegacyDeviceMenu`. Display-only screens have no inventory transaction. Custom device buttons, progress fields and packets still require their own supported protocol bridge; routing inventory through a native menu does not translate arbitrary mod packet semantics.

Closing a menu also sends the final native inventory-menu snapshot after the original close callback and the native cursor return. Vanilla cursor updates use window ID `-1`, so a delayed click reply can otherwise populate the player's inventory-menu cursor after the client has already closed the device. Sending the final snapshot also covers slots whose final value equals the inventory menu's cached value.

Original close callbacks write back only slots they changed. Close-time changes to a represented item retain its host-owned tags.

## Native items disappearing in node GUIs

The earlier close fix preserved an unchanged native-only slot, but the old inventory still imported an unmapped modern item as null. The device-menu refresh then synchronized that null to its native mirror and the client backpack. Closing restored the server's unchanged stack. Placing another item into the apparent empty slot changed the original slot from null to the incoming item, so the inventory writeback overwrote the real modern stack. This affected both item nodes and energy nodes through the shared container bridge.

Inventory transport now falls back to a stable, opaque old item carrying the native stack's complete serialized NBT, including capabilities. The original container sees an occupied slot and performs normal pickup, replacement, splitting and swapping. Conversion back restores the native identity, data and outer stack count. The embedded payload has count one so split stacks compare equal and can merge; the proxy exposes the native stack limit, capped at the old inventory limit of 64. Native rendering, cursor conversion and client prediction use this same transport. Gameplay mapping remains separate: carrying a modern item through an old inventory does not give it an old machine recipe or tool behavior.

## Reproduction and verification

Supply original Extra Utilities 1.2.12 under the disposable game's `mods/1.7.10/` and a prepared private runtime:

```powershell
.\gradlew.bat --offline --project-cache-dir build/gui-sync-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/gui-sync-validation' `
  runClient '-PmfclRunDir=build/gui-sync-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  '-PmfclGuiSyncSmokeTest'
```

The probe opens the real `GuiTransferNode` through native block interaction and uses native menu-click packets. It verifies acknowledged pickup/placement against the actual client inventory, insertion into the node's buffer and shift removal, and exact client/server inventory equality after close. With a 600 ms integrated-server stall it sends pickup, placement, number-key swap, split pickup, and drag-distribution sequences followed immediately by close. Each case checks item conservation and empty client/server cursors. Both item and energy nodes also check native-item visibility while open, replacement of an occupied native slot, split/merge, number-key swaps, native cursor return after delayed close, and serialization/stack limits of tagged netherite ingots, a damaged netherite sword, honey bottles and untagged copper ingots.

The probe creates its own world and stops its own client. Check `logs/mfcl/gui-sync-smoke.json` for `status: PASS`; a successful Gradle exit alone is insufficient. This covers a controlled server stall, not arbitrary network reordering or every mod's custom GUI packet handler.

2026-10-23: the pre-fix client reproduced `Acknowledged node GUI pickup left stale native inventory slot`. The same probe on that fix passed all ten recorded checks, including all five delayed-close sequences and the native-only inventory control. Gameplay evidence comes from the client/server probe report; the LWJGL JUnit tests exercise a different bridge.

The existing TC4 focus-pouch/hover-harness graphical probe also returned `PASS` with this build (`build/gui-sync-pouch-game/logs/mfcl/focus-pouch-smoke.json`). This checks predicted clicks under a 600 ms stall, insertion/removal restrictions, shift transfers, replacement and selected-slot invalidation, immediate pickup/close, and harness persistence across save/relogin. It complements the unpredicted Extra Utilities node path.

2026-10-24: the expanded item/energy-node probe returned `PASS` for all 20 recorded checks with opaque native inventory transport (`build/gui-sync-game/logs/mfcl/gui-sync-smoke.json`). Both node types preserved native visibility, occupied-slot replacement, split/merge, number-key swap and delayed-close cursor return. Serialization and 1/16/64 stack limits were checked for the four native item fixtures during each node sequence. The earlier TC4 report above predates this transport change.
