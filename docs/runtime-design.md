# Runtime design and ownership

Target: Forge 1.7.10 / Minecraft 1.7.10 inside Forge 1.20.1 / Java 17. This describes the current mechanisms; complete compatibility remains unfinished.

## Loading domains and original inputs

The modern Forge mod owns `LegacyRuntimeService` and `EmbeddedLegacyRuntime`. A private tool loader and a legacy LaunchWrapper loader execute original old FML, mod initialization, supported CoreMod transformers and access transformers in the same JVM as the host. Modern Minecraft classes are not a fallback for missing old classes. Shared bridge classes have an explicit owner; old libraries such as ASM remain isolated.

Original mod inputs are discovered from the host game directory's `mods/1.7.10/`. The loader checks hashes before boot and verifies the originals were not modified. Private patched/SRG runtime files are separate derived inputs, described by `runtime-manifest.json`; they are not a repackaging of the user's original mod JARs.

`logs/mfcl/legacy-1.7.10.json` records discovery and dependency diagnostics. `logs/mfcl/runtime-1.7.10.json` records actual FML stages, transformation and adapter results. `FML_AVAILABLE` and `CLIENT_DATA_AVAILABLE` describe milestones, not complete old-client support. The intentionally conservative `legacyClientReady` field remains false.

## Registration barrier

Successful old initialization freezes old declarations before the modern registration window. `ModernContentBridge` publishes host counterparts only through modern registration events. Legal old IDs are retained. Other IDs use a lowercase namespace and a readable snake-case path: `Baubles:Ring` becomes `baubles:ring`, and `Thaumcraft:ItemResearchNotes` becomes `thaumcraft:item_research_notes`. Unsupported characters are escaped individually. Normalization collisions are rejected before registration, with both original names reported.

Forge missing-mapping events migrate saved block/item references from the earlier `mfcl:legacy/<UTF-8 hex>` names to their corresponding loaded entries. The original case-sensitive IDs remain unchanged inside the legacy runtime and public API. The generated `mfcl-content-manifest.json` includes explicit block/item correspondences under `registryIds`.

For example, `/give @s baubles:ring` and `/give @s thaumcraft:item_thaumometer` use the readable registered IDs. Changing a research note's ID does not generate its research data; its metadata and original NBT still determine its state.

2026-10-19 validation: isolated `test build` passed (73 tests: 69 passed, 4 skipped for unavailable external reference inputs). `LegacyRegistryIdsTest` checks readable names, unchanged legal IDs, unsupported-character escapes, collision rejection and Forge remapping of saved hexadecimal names. The device client probe additionally checks the actual `/give` command dispatcher, `baubles:ring` completion and inventory delivery with original TC4/Baubles loaded.

Legacy metadata is preserved in host block states or item envelopes. Derived resources contain textures, language data, models and supported standard crafting definitions. They do not alter original archives. Item availability in the creative catalog does not establish gameplay support for every variant.

## Objects, state and threads

`LegacyServices` owns the shared old object domain. Calls entering old code use the serialized `LegacyClientData.inLegacy` scope, which installs and restores the old context class loader even on failure.

Item stacks cross the boundary as copies or detached snapshots. Old NBT stays in its original format, including old indexed-list SNBT. Native-only tags without an explicit mapping are rejected for gameplay conversion. Presentation-only rendering paths must not be used to validate or commit gameplay writes.

World facades preserve old `World` / `WorldProvider` types while forwarding supported operations to the host. Bound worlds have owning threads and are invalidated on unload. Client worlds and authoritative server worlds remain separate. A serialized runtime lock does not schedule work onto the correct logical thread or grant a client permission to change server state.

Selected block-entity callbacks, inventory, redstone and persistence are bridged. Unsupported operations fail explicitly; they are not proof that arbitrary old machines or world generation work. Current scope and acceptance details are in [runtime logic](runtime-logic.md).

Biome identities and column queries use an explicit old-object mapping. TC4 biome definitions, modern climate selection for new overworld presets, palette mutation and native client synchronization are described in [biome bridge](biome-bridge.md). This does not activate arbitrary legacy world generators, custom entity spawns or TC4's complete decoration/taint systems.

## Rendering and sound

Old drawing is captured as geometry, render state, transforms, textures and text, then replayed through the modern host. Supported display-list commands remain ordered and scoped to their old GL identity. Selected native GPU calls use a host backend with state restoration. This is not unrestricted OpenGL emulation.

Frozen item geometry preserves the original caller/helper/item rendering pipeline for each supported display context. Dynamic adapters use `LegacyDynamicItems.Renderer` for per-frame geometry and text. `LiveLegacyItemCapture` supplies a real client world/player for original first-person rendering and restores temporary camera/equipment state. TC4 decides which scanner/context needs that path in its own adapter. Static models continue to work when a dynamic adapter returns no drawing for a frame.

Sound bridging derives resources from original archives, maps supported identifiers and forwards local or server playback using host audio. See [rendering](special-block-rendering.md), [item previews](jei-item-rendering.md) and [sound](legacy-sounds.md) for exact implementation and acceptance evidence.

## Network and event boundary

General forwarding of arbitrary old network protocols and events is unfinished. Original GUI callbacks, selected player-state operations and the current scanner path cover narrower contracts.

The TC4 scanner captures a completion notification, then validates the actual target against the authoritative server-side old player/world before awarding knowledge. Original research/aspect/scanned-state packets are used for the implemented synchronization. This dedicated path is not a generic packet bridge and is not permission to trust arbitrary client-supplied scan data.

The core no longer names the thaumometer or its packets. `LegacyHeldScanners` lets an adapter claim a held item, recognize its completion request during the use tick and re-evaluate it on the server; TC4's `ThaumcraftScanner` implements `doScan` / `PacketScannedToServer` handling, and its state packets are sent through `LegacyPlayerState.Hooks.synchronize`. TC4 names still in the core are the biome pack / `mfcl:thaumcraft_biomes` biome source registration and the client scan-notification reader; acceptance probes also use TC4 fixtures.

Old `SimpleNetworkWrapper` and host `SimpleChannel` registrations are checked as they happen. A message ID reused for another type, an ID that aliases after byte truncation, or a second handler for the same type and direction fails the registration, so a bad legacy mod stops legacy initialization with both owners named. Old FML lets one type use one ID per side (TC4's `PacketNote` uses 18 and 21), which is accepted; a host channel must use exactly one index per Java type.

`LegacyDiagnostics` records, with counts and bounded size: host or saved objects without an original counterpart, failures of original callbacks attributed to the first non-host stack frame and its archive, and `@SubscribeEvent` handlers on the old buses whose event type was never posted (the old `EventBus.post` is instrumented). Operators query them with `/mfclapi mappings|errors|events` and write a full report with `/mfclapi dump` (`logs/mfcl/diagnostics-1.7.10.json`).

## Core and adapter boundary

The core owns generic loading, registration, object conversion, capture/replay, persistence hooks and runtime diagnostics. Optional adapters are selected by actual old mod IDs through `LegacyModAdapter`.

TC4 knowledge initialization, NBT sections and separate client/server knowledge scopes live in `compat.thaumcraft`. `LegacyPlayerState.Hooks` supplies the generic lifecycle entry points, unwinding scopes in reverse order and rolling back a partially entered chain on failure. TC4 dynamic scanner selection and unmapped-target fallback also live in that adapter. These implementations still ship with MFCL for now; moving them into a separate TC Modern Lib compatibility library is a later step.

The independent API artifact exposes opaque references, exact-descriptor MLA accessors, controlled named mappings, ready callbacks and bounded diagnostics. It contains no old or modern Minecraft classes. Host adapter SPI classes in `bridge` are not yet a stable standalone addon API. See [Developer API / MLA](developer-api.md) for concrete examples and remaining work.
