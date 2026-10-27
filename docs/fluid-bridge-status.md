# Fluid bridge implementation status

This is a source audit of the current implementation, with existing acceptance reports identified separately. Fluid support currently covers tank and container interoperability; it is not a complete publication of legacy fluids into the modern fluid registries.

| Area | Current implementation | Boundary |
| --- | --- | --- |
| Original Forge fluid registry | Original mods register their real 1.7.10 `Fluid` objects and `FluidStack` objects in the isolated runtime. | No generic catalogue exports these objects into modern `Registries.FLUID` or `ForgeRegistries.Keys.FLUID_TYPES`. |
| Original tanks | `LegacyFluidAccess` reads real `IFluidHandler.getTankInfo` values: fluid name, amount, capacity and whether fluid NBT exists. | Fluid is represented by an original registry-name string; there is no independent buffer. |
| Filling/draining | Calls original `canFill`/`canDrain`, directional fill/drain and simulation/execute APIs; validates transferred amounts and live server ownership. | Does not implement arbitrary custom fluid APIs outside Forge's original `IFluidHandler`. |
| Modern tank capability | `LegacyBlockEntity` exposes native `FLUID_HANDLER`, backed by `LegacyFluidHandler` and the actual original tank. | This direction lets native consumers interact with an old tank only for representable fluids. |
| Fluid identity mapping | Water/lava are explicit mappings. Other original names can match one already-registered native source fluid with the same path. Ambiguous namespaces are rejected. | No stable explicit old-name ↔ modern-name catalogue; MFCL does not register a missing native custom fluid. |
| Buckets and container interaction | Native fluid-container capabilities use Forge `FluidUtil` against old tanks. Creative empty-bucket pickup uses vanilla filled-result semantics. | Does not generically export old fluid-container items as modern item fluid capabilities. Availability of custom fluids still depends on identity mapping. |
| Fluid NBT | Tank queries identify tagged fluids. Native capability transfer rejects NBT-bearing resources. | No lossless `FluidStack.tag` conversion or tagged-fluid interoperability. |
| Native machines viewed by old pipes | Native FE/inventory tiles have old facades. | No equivalent generic facade exposing a native `FLUID_HANDLER` as an old `IFluidHandler`; the reverse direction remains missing. |
| World blocks | Original blocks can be mirrored, and generic old scheduled-update/collision/render entry points exist. TC4 bath-salt source conversion and purifying-bath collision have dedicated acceptance. | A mirrored block is not a native `LiquidBlock`/`FlowingFluid`: custom `FluidState`, source/flowing pairs, bucket pickup, density/viscosity/temperature/gas behavior and native swimming/buoyancy are not generically bridged. Full old fluid flow behavior has not been established by these tests. |
| Appearance | Original fluid icons can be used by old renderers; existing XU drum-item/world-model audits cover liquid colors. | That does not establish native fluid rendering or arbitrary modern renderer compatibility. |
| Persistence | The original tank is saved in the legacy tile's binary NBT and synchronized through its host envelope. | This does not create native registry identities for the saved fluid. |

Implementation entry points: [LegacyFluidAccess](../src/main/java/github/com/gengyoubo/mfcl/bridge/LegacyFluidAccess.java), [LegacyFluidHandler](../src/main/java/github/com/gengyoubo/mfcl/content/LegacyFluidHandler.java), [LegacyBlockEntity](../src/main/java/github/com/gengyoubo/mfcl/content/LegacyBlockEntity.java), the container interaction in [ModernContentBridge](../src/main/java/github/com/gengyoubo/mfcl/content/ModernContentBridge.java), and native tile lookup in [LevelWorldBridge](../src/main/java/github/com/gengyoubo/mfcl/content/LevelWorldBridge.java).

Existing reports, rather than new claims of broad compatibility:

- `build/fluid-container-audit/game/fluid-container-report.json`: PASS, 32 XU drum/bucket cases covering water/lava, creative/survival, both hands, container counts, both drum variants and insufficient fluid. See [the recorded audit and rerun command](extrautilities-text-flow-audit.md).
- `build/dropped-item-game/logs/mfcl/dropped-item-fluid-smoke.json`: PASS for original bath-salt expiration at 200 ticks and source-water conversion, with dry/flowing/ordinary-item controls. See [dropped item fluids](dropped-item-fluids.md).
- The current TC4-specific potion acceptance checks source-only purifying-bath consumption and original warp-dependent ward duration. It does not test fluid spread or modern fluid registration.

The next foundation is an explicit, namespaced old-fluid catalogue plus native fluid/type registration. Native-machine facades, fluid-container item adapters and fluid NBT round trips then need to use that same mapping. Completing those is separate from this audit.
