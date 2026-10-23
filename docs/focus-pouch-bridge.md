# TC4 focus pouch and hover harness: GUI and synchronization

2026-10-23. Reference: original Thaumcraft 4.2.3.5, Baubles 1.0.1.10, Forge 1.20.1 / 47.4.28, Java 17.

## Reproduced failure

The original `GuiFocusPouch` checks its opening hotbar slot while drawing. Native menu opening precedes its initial contents packet. MFCL mirrored the new menu's still-empty slots into the original player immediately, so the pouch GUI saw an empty opening slot and called `EntityClientPlayerMP.closeScreen`. The connection-less legacy actor then threw a null pointer exception in its old network handler, and the GUI bridge disabled drawing for that screen. The isolated pre-fix graphical probe reproduced this stack trace.

Client container binding now retains the prepared player inventory until `initializeContents` receives the authoritative snapshot. Original close requests also use the native menu close protocol. Portable GUI descriptors carry the server's opening hotbar slot so a subsequent selection change does not redefine which slot the GUI belongs to.

The MFCL network protocol advances from 8 to 9 for this descriptor change; update both client and server together.

## Latency and persistence changes

MFCL previously performed no client prediction for original container clicks: the displayed cursor/slots waited for a server reply on every operation. The TC4 adapter now explicitly opts its focus pouch and hover harness into prediction using the original client container's slot-click code. Native menu packets still run the original server container and reconcile both the menu and corresponding player inventory slots. Other original containers retain their existing behavior unless an adapter opts them in.

The original TC4 container stores a reference to the opening pouch and writes its contents only on close. Its close callback also tries to replace the currently held item. MFCL's full inventory import used to replace even equal stack objects, detaching that saved reference. Inventory imports now preserve equal objects. A TC4-only runtime adapter commits changed contents after each click and writes through the opening pouch reference, avoiding the close-time held-slot assignment. Unchanged clicks/close do not rewrite the pouch inventory NBT. Number-key swaps cannot move the opening pouch; server validity checks reject a removed or replaced pouch before processing a delayed click. Original mod JARs are unchanged.

Persistent actor exposure (`LegacyPlayerInventory.expose`) also preserves equal objects when its previous tracking baseline no longer matches. TC4-Modern's Curios goggles equipment source enables per-tick player synchronization even without worn goggles. A menu inventory import can already have applied the host's latest pouch contents before that synchronization runs. Previously, the stale exposure baseline caused MFCL to replace the equal pouch and the server's reference-based validity check closed its menu. Exposure now compares the materialized host stack before assigning it, then refreshes the host value, retained object and comparison copy. Real item/count/metadata/NBT changes and removals still apply; the portable container's reference check remains intact.

These findings establish concrete MFCL synchronization defects and an unsafe original close-time pattern. They do not establish every cause of the encyclopedia's warning, or demonstrate a universal server TPS improvement. Client prediction improves visible interaction latency; it cannot reduce network RTT or server processing time.

## Hover harness and `/hat`

Original `ItemHoverHarness.getArmorModel` ignores the requested armor slot and returns its chest model even for the helmet slot. `ModelHoverHarness.render` subsequently dereferences `player.inventory.armorItemInSlot(2)` without a null check, and later casts that chest item's type to `ItemHoverHarness`. A harness placed in the head slot by a server command can therefore render against an empty or unrelated chest slot. The inventory/equipment state persists across login, which explains how such a render fault can recur on reconnect. This is an identified original source path, not a claim that every historical `/hat` failure shares the same stack trace.

The TC4 runtime adapter returns the vanilla fallback for non-chest model requests, guards the model against missing/unrelated chest equipment before any drawing, and skips harness armor ticks when the supplied stack does not match the chest equipment. A misplaced item remains recoverable in the inventory; no player data is deleted. IC2's code is not modified by this TC4-specific fix.

The harness also retains its original right-click fuel GUI: the generic armor bridge now calls an armor subclass's custom right-click callback while ordinary armor keeps native right-click equip. Its container shares the opening-stack persistence and number-key protections with the pouch. Fuel changes commit after each click. Incoming authoritative null/player slots bypass the original harness container's incorrect fuel-only `putStackInSlot` filter, so removing fuel actually clears the client mirror. User clicks still require an original Potentia jar; number-key swaps cannot bypass that rule.

## Reproduce

Use a disposable game directory with only the reference TC4/Baubles JARs under `mods/1.7.10/` and a prepared private runtime:

```powershell
.\gradlew.bat --project-cache-dir build/pouch-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/pouch-validation' `
  runClient '-PmfclRunDir=build/pouch-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclFocusPouchSmokeTest --console=plain
```

The probe creates its own world, writes `logs/mfcl/focus-pouch-smoke.json` and `screenshots/focus-pouch.png`, then stops its client. It checks the original background texture, immediate pickup/insertion while its disposable integrated server is stalled for 600 ms, server NBT before close, retained object identity across idle synchronization, rejection of non-foci and opening-pouch swaps, shift transfers, replacement invalidation, reopen persistence, selected-slot changes and rapid pickup/close with exact cursor return.

The `inventoryExposureIdentityAndChangeTracking` check uses a temporary copy of the actor's inventory to exercise first exposure, an equal replacement object, an already-converged in-place update, subsequent legacy change detection, and authoritative count/metadata/NBT/removal changes. Run the same probe in TC4-Modern with Curios enabled to cover the equipment source's real per-tick synchronization path.

The same probe then checks the original harness GUI, Potentia insertion/removal, invalid item and number-key rejection, shift insertion, selected-slot close, original model/tick callbacks against missing and unrelated chest equipment, and normal chest model resolution. It places the harness in the head slot (the equipment result of `/hat`), saves/disconnects, loads the same world, verifies a successful login with the head item and fuel intact, then restores normal chest equipment. It does not install a `/hat` command plugin or claim to exercise its command parser. This is a controlled server-stall check, not a dedicated-server network benchmark. Worn Baubles, offhand opening, complete flight controls and IC2 jetpacks are outside this acceptance scope.
