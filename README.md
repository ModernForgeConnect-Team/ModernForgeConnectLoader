# Modern Forge Connect Loader

[English](README.md) · [简体中文](readme/README.zh-CN.md) · [日本語](readme/README.ja.md) · [Русский](readme/README.ru.md) · [Deutsch](readme/README.de.md) · [한국어](readme/README.ko.md)

Modern Forge Connect Loader (MFCL) is an experimental compatibility runtime for running original Forge 1.7.10 mods inside a Forge 1.20.1 / Java 17 host.

## Purpose and design goals

MFCL runs real legacy FML and original mod code in an isolated class-loading domain within the host JVM. Bridges connect supported game objects, registries, rendering and interactions to the modern game. Original mod JARs remain unchanged.

The core provides generic mechanisms. Mod-specific behavior belongs in optional adapters; Thaumcraft knowledge and scanner rendering are being separated into the TC4 compatibility layer. Developer API / MFCL Legacy Accessor (MLA) lets modern addons call legacy code without compiling against old Minecraft classes.

**This is a work in progress. Successful initialization or a visible item does not mean full gameplay compatibility.**

## Requirements and installation

| Component | Current target |
| --- | --- |
| Host | Minecraft 1.20.1, Forge 47.4.28 |
| Java | JDK 17 |
| Legacy runtime | Minecraft 1.7.10, Forge 10.13.4.1614, privately prepared SRG runtime |
| Validated graphical environment | Windows 11; client and integrated-server acceptance tests |

1. Build MFCL with Java 17, or use a matching build; put the full `mfcl-0.1.0.jar` in the modern game's `mods/` directory.
2. Obtain original legacy mods and their required dependencies. Put them directly in `mods/1.7.10/`, rather than the modern mods directory.
3. Prepare the private legacy runtime and its verified input manifest. See [test environment and preparation](docs/testing.md). The mod JAR and Dependency Resolver do not contain or automatically install a complete legacy Minecraft runtime.
4. Launch Forge and inspect `logs/mfcl/legacy-1.7.10.json` and `logs/mfcl/runtime-1.7.10.json` for discovery, actual loading stages and failures.

```text
<game-directory>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      original-mod.jar
      required-legacy-dependency.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...verified runtime libraries
```

MFCL reads the game's own directory and scans only JARs directly inside `mods/1.7.10/`. Original Minecraft, Forge and third-party mod files are not redistributed with MFCL.

## Implemented compatibility

- Real legacy FML initialization, supported CoreMod transformers and access transformers in the isolated legacy domain.
- Registration of modern item/block counterparts, metadata and original NBT preservation, generated textures/languages, supported placement, drops and persistence.
- Supported original GUI, font, item, block and block-entity rendering; selected live item rendering and legacy sound playback.
- Partial world, player-state, inventory, redstone and block-entity logic bridges, plus supported standard crafting-table recipes.
- Experimental Developer API / MLA, explicit object conversions, diagnostics and standalone dependency tooling.

| Reference mod | Verified scope |
| --- | --- |
| NotEnoughItems 1.0.5.120 + CodeChicken dependencies | Real initialization, inventory overlay, search, original recipe/usage screens |
| AcademyCraft 1.0.7 + LambdaLib 1.2.3 | Real initialization, basic content registration and selected placement/save checks |
| Thaumcraft 4.2.3.5 + Baubles 1.0.1.10 | Real initialization, selected rendering and gameplay bridges, Thaumonomicon, held scanning/rewards/persistence and sound |

The [technical documentation index](docs/README.md) links detailed scope, original input hashes and dated acceptance records. These results apply to the tested versions and features.

## Compatibility limits

MFCL is not a complete Forge 1.7.10 client, universal OpenGL emulator or replacement for every legacy game subsystem. Arbitrary mods, custom GUIs/renderers, machines, world generation, entities, player abilities and network protocols remain partially supported or unimplemented. Dedicated-server compatibility is not established by integrated-server tests.

Conversions reject unsupported or lossy data. Modern-only blocks/items may have no original representation. Full event forwarding and generic entity/player conversion are still pending. Standard crafting support does not imply support for arcane, crucible, infusion or other mod-specific crafting systems.

TC4's scanner completion and scan-packet capture still contain dedicated compatibility paths. Their staged migration is documented in [Developer API / adapter boundaries](docs/developer-api.md). Loaded legacy containers remain in old FML; modern ModList/network membership is not equivalent to the combined display in the mod list.

## Developer API / MLA example

Use the standalone API as `compileOnly`; do not bundle it into an addon or put old Minecraft classes on the modern runtime classpath.

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

Declare a public accessor inside your addon and subscribe from its initialization code:

```java
@LegacyType("net.minecraft.item.ItemStack")
public interface OldStack {
    @LegacyMethod(name = "func_77946_l",
                  descriptor = "()Lnet/minecraft/item/ItemStack;")
    LegacyObject copy();
}

// In the modern addon's initialization:
MfclApi.whenReady(runtime -> {
    LegacyObject stack = runtime.stack(
        new LegacyStack("minecraft:diamond", 0, 2, ""));
    LegacyObject copy = runtime.accessor(OldStack.class, stack).copy();
});
```

MLA uses exact legacy SRG names and JVM descriptors. Legacy references stay opaque and belong to one runtime; gameplay calls must run on the appropriate game thread. See the [compilable example](examples/legacy-accessor/) and [API guide](docs/developer-api.md) for imports, fields/constructors, conversions, lifecycle and limitations. `/mfclapi` provides operator diagnostics.

## Legacy Dependency Resolver

The [standalone Gradle plugin](tools/legacy-dependency-resolver/) reuses Gradle dependency resolution and accepts explicit local JAR imports. Inputs require pinned SHA-256 hashes, origin records and license/distribution notes. It does not add remote repositories, resolve transitive dependencies or install old JARs on the modern runtime classpath.

It produces content-addressed JARs and a provenance manifest. Configuration, failure behavior and tests are described in its [README](tools/legacy-dependency-resolver/README.md).

## Build, test and contribute

Run from the repository root with JDK 17:

```powershell
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
.\gradlew.bat inspectLegacyMods "-PlegacyGameDir=C:/path/to/game"
```

The full mod and API artifacts are written to `build/libs/`. Use `./gradlew` on systems that do not use the Windows wrapper. Reference checks requiring external inputs are skipped when those inputs are absent; graphical acceptance requires a prepared game directory. See [testing and reproducibility](docs/testing.md) for preparation, smoke-test flags and isolated build outputs.

Contributions should describe the target mod/version, reproduction steps, logs and expected behavior. Keep generic mechanisms in the core and mod-specific behavior in adapters. Include focused regression evidence, preserve original JARs and respect existing thread, ownership and registration contracts.

## License

MFCL is licensed under the [Mozilla Public License 2.0 (MPL-2.0)](LICENSE). Minecraft, Forge and third-party mods retain their respective licenses; MFCL's license does not grant rights to redistribute their files.
