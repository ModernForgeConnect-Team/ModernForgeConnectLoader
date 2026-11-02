# Fluid bridge implementation status

MFCL registers identities and fluid types for legacy Forge fluids, while keeping the original blocks and their scheduled flow logic authoritative.

| Area | Implementation and boundary |
| --- | --- |
| Registry and properties | The original Forge fluid catalogue is exported under stable `mfcl:legacy_fluid/…` identifiers, with density, viscosity, temperature, luminosity, icon and available bucket mapping. |
| World state | Liquid-material legacy blocks expose native `FluidState` with source and partial levels. Their original block handles flow and collision. Gas and non-liquid materials such as TC4 flux goo deliberately do not expose a world fluid state, to avoid applying vanilla water movement to them. Native flow vectors are approximate. |
| Tanks and containers | Native fluid handlers can access original Forge tanks, and registered original containers expose native item fluid capabilities. Simulation and execution use the original APIs. Original pipes accessing native tanks are not yet bridged. |
| NBT | Tagged fluids are rejected for native capability transfers; lossless fluid tag conversion is not implemented. |
| Rendering | The original block renderer draws world fluid geometry. A transparent, alpha-discarded native sprite supplies the native fluid state without obscuring the original rendering. |
| Persistence | Original tank data remains in the legacy tile's saved NBT; native identities are derived from the original fluid names. |

Implementation: [LegacyFluidAccess](../src/main/java/github/com/gengyoubo/mfcl/bridge/LegacyFluidAccess.java), [LegacyFluidRegistry](../src/main/java/github/com/gengyoubo/mfcl/content/LegacyFluidRegistry.java), [LegacyFluidHandler](../src/main/java/github/com/gengyoubo/mfcl/content/LegacyFluidHandler.java), and [LegacyFluidItemHandler](../src/main/java/github/com/gengyoubo/mfcl/content/LegacyFluidItemHandler.java). TC4-specific behavior and test boundaries are documented in [the TC4 fluid audit](thaumcraft-fluid-audit.md).

The `build/fluid-native-verify/game/logs/mfcl/dropped-item-fluid-smoke.json` report records a passing TC4 flow, bucket, collision, native immersion and XU tank/container capability run. The accompanying `screenshots/mfcl-fluid-visual.png` records the alpha-discard rendering fixture. These checks do not establish compatibility with every fluid or machine.
