# Original GUI inventory and close synchronization

Extra Utilities' item transfer node uses the common `LegacyDeviceMenu` bridge. Its GUI does not opt into local click prediction. Previously, native player-inventory reconciliation was registered only for predicted containers, so authoritative node-menu slots could update while the client's actual backpack/hotbar retained their old values.

Every original GUI now binds its player-inventory slot map independently of prediction. Both complete menu snapshots and individual slot updates reconcile those native inventory slots. Containers still opt into prediction individually; the original server container continues to decide each click.

This is shared container infrastructure, not an Extra Utilities class-name exception. Captured FML block/portable GUI requests, original `IInventory` chest requests, and registered device providers all open the same native `LegacyDeviceMenu`. Display-only screens have no inventory transaction. Custom device buttons, progress fields and packets still require their own supported protocol bridge; routing inventory through a native menu does not translate arbitrary mod packet semantics.

Closing a menu also sends the final native inventory-menu snapshot after the original close callback and the native cursor return. Vanilla cursor updates use window ID `-1`, so a delayed click reply can otherwise populate the player's inventory-menu cursor after the client has already closed the device. Sending the final snapshot also covers slots whose final value equals the inventory menu's cached value.

Original close callbacks now write back only slots they changed. Native items without a legacy representation were imported as null and must remain in the host inventory when the original null slot is unchanged. Close-time changes to a represented item retain its host-owned tags.

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

The probe opens the real `GuiTransferNode` through the native block interaction and uses native menu-click packets. It verifies acknowledged pickup/placement against the actual client inventory, insertion into the node's buffer and shift removal, and exact client/server inventory equality after close. With a 600 ms integrated-server stall it sends pickup, placement, number-key swap, split pickup, and drag-distribution sequences followed immediately by close. Each case checks item conservation and empty client/server cursors. A tagged netherite ingot supplies a native-only inventory negative control for close-time data loss.

The probe creates its own world and stops its own client. Check `logs/mfcl/gui-sync-smoke.json` for `status: PASS`; a successful Gradle exit alone is insufficient. This covers a controlled server stall, not arbitrary network reordering or every mod's custom GUI packet handler.

2026-10-23: the pre-fix client reproduced `Acknowledged node GUI pickup left stale native inventory slot`. The same probe on the fix passed all ten recorded checks, including all five delayed-close sequences and the native-only inventory control. `runClient build` succeeded; the project currently has no JUnit test sources, so the gameplay evidence is the client/server probe report rather than the no-source `test` task.

The existing TC4 focus-pouch/hover-harness graphical probe also returned `PASS` with this build (`build/gui-sync-pouch-game/logs/mfcl/focus-pouch-smoke.json`). This checks predicted clicks under a 600 ms stall, insertion/removal restrictions, shift transfers, replacement and selected-slot invalidation, immediate pickup/close, and harness persistence across save/relogin. It complements the unpredicted Extra Utilities node path.
