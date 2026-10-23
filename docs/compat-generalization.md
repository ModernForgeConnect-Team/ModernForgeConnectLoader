# Compatibility mechanisms and mod-specific declarations

The compatibility packages supply original class/field names, data formats and gameplay decisions. Shared host lifecycle, transport and synchronization belong in `bridge`, `content`, or the old runtime's common support code. This pass reviewed the `codechicken`, `extrautils`, `ic2`, `nei` and `thaumcraft` packages using that boundary.

## Extracted mechanisms

| Mechanism | Shared implementation | What the adapter still supplies |
| --- | --- | --- |
| Portable-container identity, protected slot, number-key validation, changed-content commit and stale-container invalidation | Old-runtime `PortableContainerAdapter` / `PortableContainerHooks.Policy` | Original container name, opening stack/player/world/contents access, and serialization of those contents |
| Modern crossbow loading selection, original projectile replacement, native trajectory/critical state and multishot | `LegacyServices.crossbowAmmo()` / `HostCrossbowProjectiles` | Original ammunition key and a factory for its original arrow class/metadata |
| Packet sender dimension/identity/name and open-device coordinate binding | `LegacyPacketTargets` | Protocol field names and additional gameplay validation |
| Native Forge class-loader context during adapter initialization | `LegacyModAdapter.withHostContext` | Native listeners to install |

TC4's two portable adapters now declare policies over the common transformer. The common code derives the protected native inventory slot from the actual original `Slot`, without TC4's former hotbar offsets. Number-key input respects both the original slot and inventory restrictions. The focus pouch retains its original `setInventory` format; the harness retains its `jar` compound. The authoritative inbound-slot override is an explicit adapter option for containers with a restrictive original setter. The transformer remains opt-in and checks the expected method layout, since replacing an arbitrary mod's close/validity method without knowing its storage contract is unsafe.

The crossbow adapter now only registers `Thaumcraft:PrimalArrow` and constructs `EntityPrimalArrow` with the ammunition's metadata. A factory constructs and returns an unspawned old `EntityArrow`; the shared bridge applies the native trajectory/critical flag and spawns it. The bridge handles main/off-hand charged crossbows and replaces the native arrow only after successful original spawning. Factory conflicts fail explicitly. Unregistered ammunition follows the normal host path; the bow still uses the original mod's own bow events. This factory contract covers original `EntityArrow` subclasses, not arbitrary projectile classes.

Packet validation helpers do not authorize arbitrary serverbound messages. TC4 still declares its supported messages and validates research existence, held wand/focus, research-note coordinates and aspect availability. IC2's custom byte stream and other custom packet codecs retain their existing adapters.

## Package audit

| Package / functionality | Boundary after review |
| --- | --- |
| CodeChicken namespace/reflection compatibility | Keeps library-specific namespace declarations over the common bytecode adapter mechanism |
| Extra Utilities travel, trading post and ring flight | Already uses `LegacyTravel`, `LegacyDimensions`, `RemoteMerchants`, `HostFlightGrants` and shared original-player sessions; its portal variants, trading range, ring NBT and notifier are mod-specific |
| IC2 GUI, network stream and EU/world callbacks | Already uses generic GUI providers, raw packet receivers, tile tick policy and world lifecycle hooks; `IHasGui`, stream decoder and EU subscription rules remain IC2-specific |
| NEI backend and GUI | Already implements the common recipe/input/screen hooks; NEI handler discovery, sorting and read-only recipe-mode policy remain library-specific |
| TC4 knowledge/scanner/dynamic rendering | Already registers with `LegacyPlayerState`, `LegacyHeldScanners` and `LegacyDynamicItems`; research maps, aspect rules and scan completion remain TC4-specific |
| TC4 dimensions, enchantments, equipment, effects and controls | Reuses dimension/travel, item/player, geometry capture and flight/equipment mechanisms; original maze data, vis costs, fuel, HUD/particle engines and wand messages remain in the adapter |

These host SPI contracts are internal compatibility extension points, not additions to the independent stable API JAR. Adding a different mod should require declarations/factories/policies over these mechanisms rather than a copy of their host lifecycle.

## Verification

`-PmfclServerSmokeTest=compat` runs `CompatBridgeHostSmokeProbe` in a disposable dedicated-server game with Extra Utilities and **without TC4**. It checks class-loader restoration after nested/throwing calls, authenticated packet binding and rejection controls, an independent portable-container policy with a different slot layout and output-slot restrictions, replacement invalidation, and vanilla-arrow factories on native main/off-hand and multishot crossbows. Read `logs/mfcl/compat-bridge-smoke.json`.

```powershell
.\gradlew.bat --offline --project-cache-dir build/compat-common-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/compat-common-validation' `
  runServer '-PmfclRunDir=build/compat-common-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' '-PmfclServerSmokeTest=compat'
```

The game needs the separately supplied original Extra Utilities JAR and an accepted EULA. TC4 regression games instead need original TC4/Baubles. For a fresh graphical test directory, set `onboardAccessibility:false` in its `options.txt` so a title-screen-driven probe reaches the title screen. The existing `crossbow`, `mfclFocusPouchSmokeTest` and `mfclDeviceSmokeTest` probes exercise original elemental impact, portable storage/prediction/close/relogin, and real research GUI packet paths. Check their JSON statuses as well as Gradle's exit status.

2026-10-23: the no-TC4 shared-mechanism probe returned `PASS` with Extra Utilities 1.2.12. With TC4 4.2.3.5/Baubles 1.0.1.10, the original crossbow impact, focus-pouch/hover-harness and device/research GUI probes all returned `PASS`. Reports are under `build/compat-common-game/logs/mfcl/compat-bridge-smoke.json` and `build/compat-tc-game/logs/mfcl/{server-1.7.10-smoke-crossbow,focus-pouch-smoke,device-smoke}.json`. The final isolated `runClient build` succeeded. The project has no current JUnit sources; these are actual dedicated-server and integrated-client acceptance probes.
