# Hosted golem right-click interaction

TC4 golems live inside `LegacyHostedEntity`, which extends the modern `Entity` rather than `LivingEntity`. Minecraft therefore never invokes the held item's `interactLivingEntity` callback when a player right-clicks that envelope. The golem bell's original callback, which records the selected golem's ID, home, facing and markers, was unreachable.

The envelope now forwards right-clicks through the shared inventory, packet and menu transaction. The original entity's `interactFirst` runs first; if it declines, the original item's living-target callback runs when the original target is living. This also permits the golem's own core-insertion callback and empty-hand interaction. Main/off-hand writes return to the correct host slot, and original entity state and DataWatcher changes are synchronized after the interaction. Interaction errors are logged without discarding the golem.

## Verification

Use TC4 4.2.3.5 and Baubles 1.0.1.10 in a disposable game's `mods/1.7.10/`, with its EULA accepted:

```powershell
.\gradlew.bat --offline --project-cache-dir build/golem-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/golem-validation' `
  runServer '-PmfclRunDir=build/golem-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  '-PmfclServerSmokeTest=golem'
```

Read `build/golem-game/logs/mfcl/golem-smoke.json`; the Gradle exit code alone does not establish success. The probe dispatches through real `Player.interactOn` and checks survival/creative main-hand binding, sneaking rebinding, off-hand binding and inventory conservation, original ID/home/facing/markers, bell NBT round-trip, original entity core insertion and consumption, lazy restoration of a saved golem before interaction, empty-hand safety and a vanilla non-golem negative control. It does not automate graphical clicks or the client marker overlay.
