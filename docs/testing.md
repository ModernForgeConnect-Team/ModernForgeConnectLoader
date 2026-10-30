# Test environment and reproducibility

Commands below run from the repository root. Development targets JDK 17, Minecraft 1.20.1 / Forge 47.4.28 and privately prepared Minecraft 1.7.10 / Forge 10.13.4.1614 SRG inputs. Graphical acceptance has been performed on Windows 11 with a client and integrated server.

## Builds and external inputs

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
```

Use `./gradlew` outside Windows. Initial Gradle dependency resolution requires access to the configured repositories; `--offline` is available once inputs are cached. The mod and API outputs are in `build/libs/`.

Unit tests do not require redistribution of any reference mod. Tests needing original reference JARs or a prepared runtime explicitly skip when their inputs are absent. The standalone resolver tests use local Maven/JAR fixtures for the actual resolution checks; downloading the test framework on the first build is separate.

Pinned versions, origins and hashes are recorded in:

- [NEI / CodeChicken inputs](nei-reference-inputs.json).
- [AcademyCraft / LambdaLib inputs](academy-reference-inputs.json).
- [Thaumcraft / Baubles inputs](thaumcraft-reference-inputs.json).
- [Legacy language libraries](legacy-language-inputs.json).

Original mod JARs must be supplied separately. Preparation creates derived private runtime files and test fixtures. The [standalone Dependency Resolver](../tools/legacy-dependency-resolver/README.md) verifies declared dependencies; it does not apply Forge binary patches or produce a complete SRG runtime by itself.

## Runtime installation

Only the target game directory is required:

```powershell
.\tools\prepare-legacy-runtime.ps1 -GameDirectory 'C:/mfcl-test-game' -Boot
```

The script uses `-JavaHome`, then `JAVA_HOME`, then a Gradle-provisioned JDK 17. It downloads the pinned Forge, launchwrapper and library inputs, Mojang's 1.7.10 client and MCP mappings, verifies every hash, applies Forge client patches and SRG mappings, and installs the private runtime and `runtime-manifest.json` into `mfcl/runtime/1.7.10/`. `-Boot` then starts legacy FML against that runtime and fails if it does not boot. Put original mods in the game's `mods/1.7.10/` afterwards. Nothing is redistributed; all derived files stay on the local machine.

Adding `-NeiJar 'C:/private-inputs/NotEnoughItems-1.7.10-1.0.5.120-universal.jar'` also prepares the NEI reference fixture and installs NEI and CodeChicken into the game. NEI is no longer required to install the runtime.

For TC4 validation, use a separate game directory with only original Thaumcraft and its Baubles prerequisite in `mods/1.7.10/`, and point to the already prepared runtime:

```powershell
.\tools\prepare-thaumcraft-probe.ps1 `
  -ThaumcraftJar 'C:/private-inputs/Thaumcraft-1.7.10-4.2.3.5.jar' `
  -BaublesJar 'C:/private-inputs/Baubles-1.7.10-1.0.1.10.jar' `
  -JavaHome $env:JAVA_HOME `
  -LegacyRuntimeDir 'C:/mfcl-test-game/mfcl/runtime/1.7.10' `
  -RunChecks
```

The script uses `build/thaumcraft-smoke/game` for the TC4 fixture. Supplying `-BaublesJar` makes the local prerequisite explicit; without it the existing script may download its pinned reference dependency. Consult script parameters and input manifests before changing fixture versions. Reference files for different mod sets should be kept in separate game directories.

## Isolated build outputs

When another development client is running, compiling into the same class output may replace classes that its class loader still needs. Use both a separate project cache and a separate build output. The checked-in init script supports this without changing the normal build:

```powershell
.\gradlew.bat --project-cache-dir build/validation-unit-cache `
  -I tools/isolated-build.init.gradle `
  -PmfclValidationBuildDir=build/validation-unit `
  test assemble -PlegacySupportDir=build/nei-investigation
```

`legacySupportDir` points to a prepared support root containing `runtime/`. Omit it if those inputs are unavailable; relevant runtime integration tests then skip. Reference NEI checks additionally use `-PneiJar=<original-file>`.

For graphical acceptance, use a different output/cache from both the normal build and unit validation:

```powershell
.\gradlew.bat --project-cache-dir build/validation-client-cache `
  -I tools/isolated-build.init.gradle `
  -PmfclValidationBuildDir=build/validation-client `
  runClient -PmfclRunDir=build/thaumcraft-smoke/game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime `
  -PmfclApiSmokeTest
```

The example requires the prepared TC4/Baubles fixture because API acceptance checks an actual old mod's NBT as well as vanilla item/world mappings. Do not rebuild that output while its client is running. Automated graphical probes create their own test worlds and stop their own client when finished.

## Acceptance probes

Enable one primary probe per run; these are developer test modes, not normal launch options:

| Gradle property | Scope | Report under the selected game's `logs/mfcl/` |
| --- | --- | --- |
| `mfclHostSmokeTest` | Main menu, complete legacy block/item registration, vanilla disc/seamless-slab round trips, and native parsing of every exported crafting/smelting recipe; no world gameplay is tested | `host-startup-smoke.json` |
| `mfclApiSmokeTest` | Public provider/ready callback, native stack/NBT copies, world identity/thread/unload guards, diagnostic command | `api-smoke.json` |
| `mfclScannerSmokeTest` | TC4 held block/drop scans, real rewards/audio, cancellation/repeat/prerequisites, drop metadata and entity-ID validation, unmapped drops, persistence and dynamic lens/target coverage | `scanner-smoke.json` |
| `mfclBiomeSmokeTest` | TC4 dynamic biome registry/colors, normal-world natural biome chunks, original biome mutation, native sync, negative coordinates/neighbors and save reload | `biome-smoke.json` |
| `mfclBookSmokeTest` | Original Thaumonomicon GUI and interaction | Consult the book [acceptance record](thaumonomicon-progress.json) |
| `mfclWandAudit` | Native wand crafting/charging, wand and staff focus equip/remove, sceptre selector rejection without inventory changes; with Extra Utilities installed, rebasing a negative-coordinate transfer-node snapshot before NBT inventory callbacks and contextual rendering | `wand-host-audit.json` |
| `mfclPotionSmokeTest` | Original TC4 potion callbacks, native spell costs, warp cadence/ward/wuss, bath/soap rules, original client knowledge/fog/vignette packets, sprites/screen filters, duration/expiry and curative/warp data across world reload | `potion-smoke.json` |
| `mfclLogicSmokeTest` | Supported native/legacy block and inventory logic | Consult the logic [acceptance record](runtime-logic-progress.json) |
| `mfclCraftingSmokeTest` | Standard workbench recipes, native output consumption, JEI projection and reload | `crafting-smoke.json` |
| `mfclDeviceSmokeTest` | JEI creative tabs, original device GUIs, server slot restrictions/clicks/drops, carried-stack close, tile subtype conversion, research-aspect combinations/persistence, note insertion/place/erase/sync, native reveal/learning and lifter motion | `device-smoke.json` |
| `mfclGuiSyncSmokeTest` | Extra Utilities item/energy-node GUIs, native-only item visibility, occupied-slot replacement, split/merge, number-key swaps, stack data/limits, native player-slot reconciliation, buffer insertion/shift removal, delayed pickup/place/swap/split/drag followed by close and cursor conservation ([details](gui-inventory-sync.md)) | `gui-sync-smoke.json` |
| `mfclFocusPouchSmokeTest` | TC4 pouch/harness original GUIs, click prediction during simulated server stalls, authoritative storage, shift transfers, item restrictions, replacement/close/cursor conservation, harness model guards and head-slot equipment across real save/relogin | `focus-pouch-smoke.json` |
| `mfclDroppedItemFluidSmokeTest` | Native dropped-item lifespans and TC4 bath-salt expiration: natural water-source conversion after 200 ticks, flowing-water rejection, dry/ordinary/stacked item controls | `dropped-item-fluid-smoke.json` |
| `mfclEnchantmentSmokeTest` | TC4 native registrations/table selection, commands/books/anvils/grindstones, numeric NBT migration, original repair vis costs and haste motion, Outer Lands and enchanted item save/reload ([details](enchantment-bridge.md)) | `enchantment-smoke.json` |
| `mfclElementalToolsSmokeTest` | TC4 sword lift/repulsion/fall reduction/wear, axe attraction and item conservation, original pickaxe ore detection/overlay/depth state/expiry and server wear ([details](elemental-tools.md)) | `elemental-tools-smoke.json` |
| `mfclGeneratorSmokeTest` | Original Extra Utilities generator, GUI/coal clicks/sound, and bidirectional RF/FE capability transfers with side/rate/simulation, conservation, rollback, persistence and invalidation checks | `generator-smoke.json` |
| `mfclTooltipSmokeTest` | Actual JEI single/batch item rendering followed by tooltip depth verification | `jei-tooltip-smoke.json` |
| `mfclContentSmokeTest` | Reference content registration, rendering, placement and persistence | `content-1.7.10-smoke.json` |

For research-table rendering measurements, add `-PmfclGuiPerformanceTest` to the device probe. It seeds all discovered aspects and records warmed GUI replay CPU timings; see [research GUI performance](research-gui-performance.md) for the command, visual comparison and scope.

### Dedicated server

`mfclServerSmokeTest` runs on `runServer` without any graphical environment, in two launches against the same world. `write` places an original TC4 tile with an original inventory and an original NBT item in a host chest, lets it tick, saves and stops; `verify` restarts and checks the reloaded state. Both write `server-1.7.10-smoke-<phase>.json`, including runtime diagnostic counts:

```powershell
.\gradlew.bat runServer -PmfclRunDir=build/dedicated-server -PlegacyRuntimeDir=<runtime> -PmfclServerSmokeTest=write
.\gradlew.bat runServer -PmfclRunDir=build/dedicated-server -PlegacyRuntimeDir=<runtime> -PmfclServerSmokeTest=verify
```

The game directory needs `eula.txt` and TC4/Baubles in `mods/1.7.10/`. Client connection, disconnect/reconnect and multi-player behaviour on a dedicated server are not covered by this probe.

`-PmfclServerSmokeTest=gameplay` instead runs the door, hellbat, loot and elemental-arrow regressions once and writes `gameplay-smoke.json`. Use a disposable game directory: it creates and clears test entities and blocks. See the [gameplay fix record](gameplay-fixes-2026-10-21.md) for the isolated command and coverage.

`-PmfclServerSmokeTest=melee` checks original TC4 swords and tools against a vanilla iron sword: the original attack modifier, two native attacks with Sharpness V, sweeping (swords only), which sword/digger enchantments apply and what the table rolls, and that host enchantments survive the original hit callbacks. It writes `server-1.7.10-smoke-melee.json`; use a disposable game directory.

`-PmfclServerSmokeTest=bore` checks native pickaxes in the original TC4 bore, including native wear/enchantments, NBT save/load, transfers and modern Tinkers break/stop/repair. Optional `-PtinkersTestVersion` enables real Tinkers/Mantle inputs. See [Arcane Bore tools](arcane-bore-tools.md) for the command and `bore-smoke.json` coverage.

`-PmfclServerSmokeTest=crucible` checks an original TC4 crucible recipe through native toss events and entity collisions, research gating, water/aspect consumption, special output identity and survival pickaxe breaking/drops. See [crucible bridge](crucible-bridge.md) for the isolated command, acceptance record and coverage limits.

`-PmfclServerSmokeTest=compat` checks the shared portable-container policy, packet-target validation, native class-loader scopes and registered crossbow factories without TC4 installed. It uses a disposable Extra Utilities game and writes `compat-bridge-smoke.json`. See [compatibility generalization](compat-generalization.md) for the command, boundaries and reference-mod regressions.

`-PmfclServerSmokeTest=golem-audit` records the 208 body/core/advanced combinations and natural navigation. `golem-jobs` records 1200 ticks of representative natural tasks; `golem-reload` checks NBT restoration with a registered player. These investigation probes report known failures and use `COMPLETE` for completion, not universal compatibility. Use disposable TC4/Baubles worlds. See [the golem audit](golem-function-audit.md) for the results and coverage limits.

Additional NEI and GPU workflows are provided by `tools/test-nei-host.ps1` and existing flags such as `mfclNeiWorldSmokeTest`, `mfclOpenGlSmokeTest` and `mfclModListSmokeTest`. Scope and limitations are linked from the [documentation index](README.md).

Extra Utilities input and focused regression/graphical commands are recorded in [the compatibility fix record](extrautilities-startup.md). The original-JAR regression uses `-PextrautilitiesJar=<original-file>` with `-PlegacySupportDir=<support-root>` and skips explicitly when either input is missing.

Check both the process exit status and the probe JSON `status` / recorded assertions. A clean Gradle exit alone is not sufficient evidence that every in-game assertion passed. Preserve logs when reporting a new failure; source-controlled acceptance JSON is a dated summary, not a promise about untested mod combinations.

Plain `runClient` uses `run/` unless `-PmfclRunDir` is supplied. Preparing `build/thaumcraft-smoke/game` does not populate that default directory. To use TC4 in a normal development session, install original TC4/Baubles into `run/mods/1.7.10/` and the prepared runtime plus its verified manifest into `run/mfcl/runtime/1.7.10/`, or launch with the fixture directory and `-PlegacyRuntimeDir` explicitly. `run/logs/mfcl/legacy-1.7.10.json` reporting `NO_LEGACY_MODS` means no old mod archives were found in that run's directory.
