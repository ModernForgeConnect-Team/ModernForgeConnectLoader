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

## Existing reference preparation workflows

The existing full runtime preparation script uses the NEI reference workflow as its bootstrap input:

```powershell
.\tools\prepare-legacy-runtime.ps1 `
  -NeiJar 'C:/private-inputs/NotEnoughItems-1.7.10-1.0.5.120-universal.jar' `
  -JavaHome $env:JAVA_HOME `
  -GameDirectory 'C:/mfcl-test-game'
```

This workflow downloads the pinned runtime dependencies from the sources recorded by the scripts/specifications, verifies inputs, applies Forge client patches and SRG mappings, and installs the private runtime into the chosen game's `mfcl/runtime/1.7.10/`. It also prepares NEI reference mods. It is a developer reference workflow, not a universal installer and not a requirement that every production legacy-mod set use NEI.

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
| `mfclApiSmokeTest` | Public provider/ready callback, native stack/NBT copies, world identity/thread/unload guards, diagnostic command | `api-smoke.json` |
| `mfclScannerSmokeTest` | TC4 held block/drop scans, real rewards/audio, cancellation/repeat/prerequisites, drop metadata and entity-ID validation, unmapped drops, persistence and dynamic lens/target coverage | `scanner-smoke.json` |
| `mfclBookSmokeTest` | Original Thaumonomicon GUI and interaction | Consult the book [acceptance record](thaumonomicon-progress.json) |
| `mfclLogicSmokeTest` | Supported native/legacy block and inventory logic | Consult the logic [acceptance record](runtime-logic-progress.json) |
| `mfclCraftingSmokeTest` | Standard workbench recipes, native output consumption, JEI projection and reload | `crafting-smoke.json` |
| `mfclDeviceSmokeTest` | JEI creative tabs, original device GUIs, server slot restrictions/clicks/drops, carried-stack close, tile subtype conversion, research-aspect combinations/persistence, note insertion/place/erase/sync, native reveal/learning and lifter motion | `device-smoke.json` |
| `mfclTooltipSmokeTest` | Actual JEI single/batch item rendering followed by tooltip depth verification | `jei-tooltip-smoke.json` |
| `mfclContentSmokeTest` | Reference content registration, rendering, placement and persistence | `content-1.7.10-smoke.json` |

Additional NEI and GPU workflows are provided by `tools/test-nei-host.ps1` and existing flags such as `mfclNeiWorldSmokeTest`, `mfclOpenGlSmokeTest` and `mfclModListSmokeTest`. Scope and limitations are linked from the [documentation index](README.md).

Check both the process exit status and the probe JSON `status` / recorded assertions. A clean Gradle exit alone is not sufficient evidence that every in-game assertion passed. Preserve logs when reporting a new failure; source-controlled acceptance JSON is a dated summary, not a promise about untested mod combinations.

Plain `runClient` uses `run/` unless `-PmfclRunDir` is supplied. Preparing `build/thaumcraft-smoke/game` does not populate that default directory. To use TC4 in a normal development session, install original TC4/Baubles into `run/mods/1.7.10/` and the prepared runtime plus its verified manifest into `run/mfcl/runtime/1.7.10/`, or launch with the fixture directory and `-PlegacyRuntimeDir` explicitly. `run/logs/mfcl/legacy-1.7.10.json` reporting `NO_LEGACY_MODS` means no old mod archives were found in that run's directory.
