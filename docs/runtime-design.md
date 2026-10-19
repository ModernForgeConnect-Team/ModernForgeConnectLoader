# Runtime design and ownership

Target: Forge 1.7.10 / Minecraft 1.7.10 inside Forge 1.20.1 / Java 17. This describes the current mechanisms; complete compatibility remains unfinished.

## Loading domains and original inputs

The modern Forge mod owns `LegacyRuntimeService` and `EmbeddedLegacyRuntime`. A private tool loader and a legacy LaunchWrapper loader execute original old FML, mod initialization, supported CoreMod transformers and access transformers in the same JVM as the host. Modern Minecraft classes are not a fallback for missing old classes. Shared bridge classes have an explicit owner; old libraries such as ASM remain isolated.

Original mod inputs are discovered from the host game directory's `mods/1.7.10/`. The loader checks hashes before boot and verifies the originals were not modified. Private patched/SRG runtime files are separate derived inputs, described by `runtime-manifest.json`; they are not a repackaging of the user's original mod JARs.

`logs/mfcl/legacy-1.7.10.json` records discovery and dependency diagnostics. `logs/mfcl/runtime-1.7.10.json` records actual FML stages, transformation and adapter results. `FML_AVAILABLE` and `CLIENT_DATA_AVAILABLE` describe milestones, not complete old-client support. The intentionally conservative `legacyClientReady` field remains false.

## Registration barrier

Successful old initialization freezes old declarations before the modern registration window. `ModernContentBridge` publishes host counterparts only through modern registration events. Legal old IDs are retained; IDs containing characters disallowed by the modern registry use deterministic `mfcl:legacy/<UTF-8 hex>` IDs. The generated content manifest records these correspondences.

Legacy metadata is preserved in host block states or item envelopes. Derived resources contain textures, language data, models and supported standard crafting definitions. They do not alter original archives. Item availability in the creative catalog does not establish gameplay support for every variant.

## Objects, state and threads

`LegacyServices` owns the shared old object domain. Calls entering old code use the serialized `LegacyClientData.inLegacy` scope, which installs and restores the old context class loader even on failure.

Item stacks cross the boundary as copies or detached snapshots. Old NBT stays in its original format, including old indexed-list SNBT. Native-only tags without an explicit mapping are rejected for gameplay conversion. Presentation-only rendering paths must not be used to validate or commit gameplay writes.

World facades preserve old `World` / `WorldProvider` types while forwarding supported operations to the host. Bound worlds have owning threads and are invalidated on unload. Client worlds and authoritative server worlds remain separate. A serialized runtime lock does not schedule work onto the correct logical thread or grant a client permission to change server state.

Selected block-entity callbacks, inventory, redstone and persistence are bridged. Unsupported operations fail explicitly; they are not proof that arbitrary old machines or world generation work. Current scope and acceptance details are in [runtime logic](runtime-logic.md).

## Rendering and sound

Old drawing is captured as geometry, render state, transforms, textures and text, then replayed through the modern host. Supported display-list commands remain ordered and scoped to their old GL identity. Selected native GPU calls use a host backend with state restoration. This is not unrestricted OpenGL emulation.

Frozen item geometry preserves the original caller/helper/item rendering pipeline for each supported display context. Dynamic adapters use `LegacyDynamicItems.Renderer` for per-frame geometry and text. `LiveLegacyItemCapture` supplies a real client world/player for original first-person rendering and restores temporary camera/equipment state. TC4 decides which scanner/context needs that path in its own adapter. Static models continue to work when a dynamic adapter returns no drawing for a frame.

Sound bridging derives resources from original archives, maps supported identifiers and forwards local or server playback using host audio. See [rendering](special-block-rendering.md), [item previews](jei-item-rendering.md) and [sound](legacy-sounds.md) for exact implementation and acceptance evidence.

## Network and event boundary

General forwarding of arbitrary old network protocols and events is unfinished. Original GUI callbacks, selected player-state operations and the current scanner path cover narrower contracts.

The TC4 scanner captures a completion notification, then validates the actual target against the authoritative server-side old player/world before awarding knowledge. Original research/aspect/scanned-state packets are used for the implemented synchronization. This dedicated path is not a generic packet bridge and is not permission to trust arbitrary client-supplied scan data.

`ItemThaumometer.doScan()` handling and `PacketScannedToServer` capture are temporarily retained. Their migration belongs to the TC4 adapter after the corresponding generic interaction/network contracts are available.

## Core and adapter boundary

The core owns generic loading, registration, object conversion, capture/replay, persistence hooks and runtime diagnostics. Optional adapters are selected by actual old mod IDs through `LegacyModAdapter`.

TC4 knowledge initialization, NBT sections and separate client/server knowledge scopes live in `compat.thaumcraft`. `LegacyPlayerState.Hooks` supplies the generic lifecycle entry points, unwinding scopes in reverse order and rolling back a partially entered chain on failure. TC4 dynamic scanner selection and unmapped-target fallback also live in that adapter. These implementations still ship with MFCL for now; moving them into a separate TC Modern Lib compatibility library is a later step.

The independent API artifact exposes opaque references, exact-descriptor MLA accessors, controlled named mappings, ready callbacks and bounded diagnostics. It contains no old or modern Minecraft classes. Host adapter SPI classes in `bridge` are not yet a stable standalone addon API. See [Developer API / MLA](developer-api.md) for concrete examples and remaining work.
