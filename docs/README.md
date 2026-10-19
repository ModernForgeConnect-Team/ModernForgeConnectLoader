# Technical documentation and acceptance records

The [project homepage](../README.md) introduces MFCL, installation, compatibility limits and development. Translations are in [readme/](../readme/). This directory contains implementation details, research, pinned input specifications and dated verification records. Existing research and some implementation notes are written in Chinese.

Read each record's date, tested versions and stated limits. A historical experiment or an initialization result is not a declaration of complete compatibility.

## Runtime and development

| Topic | Documentation |
| --- | --- |
| Runtime ownership, isolation, registration and object contracts | [Runtime design](runtime-design.md) |
| Java/Forge versions, private inputs, test preparation and isolated outputs | [Test environment and reproducibility](testing.md) |
| Developer API, MLA, lifecycle, conversions and TC4 adapter boundaries | [Developer API / MLA](developer-api.md) |
| API and adapter regression results | [Developer API acceptance record](developer-api-progress.json) |
| Explicit Gradle resolution, local imports, hashes and provenance | [Legacy Dependency Resolver](../tools/legacy-dependency-resolver/README.md) |
| Initial feasibility research | [First investigation](research-forge-1710-to-1201.md), [second investigation](research-round2/README.md) |
| Earlier project overview, preserved as history | [Archived overview, 2026-10-19](runtime-history-2026-10-19.md) |

## Reference mods

| Reference | Results and inputs |
| --- | --- |
| NotEnoughItems / CodeChicken | [Acceptance record](nei-progress.json), [pinned inputs](nei-reference-inputs.json), [original implementation research](research-round2/README.md) |
| AcademyCraft / LambdaLib | [Acceptance record](academy-progress.json), [pinned inputs](academy-reference-inputs.json) |
| Thaumcraft 4 / Baubles | [Initial acceptance](thaumcraft-progress.json), [pinned inputs](thaumcraft-reference-inputs.json), [runtime logic](runtime-logic.md), [logic acceptance](runtime-logic-progress.json) |
| Thaumonomicon | [GUI implementation](thaumonomicon-bridge.md), [acceptance record](thaumonomicon-progress.json) |
| Thaumometer | [Scanning, authority and persistence](thaumometer-bridge.md), [acceptance record](thaumometer-progress.json) |

## Rendering, placement, sound and networking

| Topic | Documentation |
| --- | --- |
| Special block and block-entity rendering | [Rendering implementation](special-block-rendering.md) |
| Thaumcraft rendering audit | [Audit notes](thaumcraft-rendering-audit.md), [audit data](thaumcraft-rendering-audit.json) |
| Block placement coverage | [Placement audit](block-placement-audit.md), [audit data](block-placement-audit.json) |
| Original NBT and JEI item previews | [Item rendering](jei-item-rendering.md) |
| Original device containers, slot authority and tile synchronization | [Device container bridge](device-containers.md) |
| Sound resources and playback routing | [Legacy sound bridge](legacy-sounds.md) |
| Legacy packets and current TC4-specific path | [Runtime design: network boundary](runtime-design.md#network-and-event-boundary), [scanner bridge](thaumometer-bridge.md), [migration boundary](developer-api.md) |
| Legacy Scala / Typesafe inputs | [Pinned language-runtime inputs](legacy-language-inputs.json) |

Machine-local logs, generated packs, screenshots, temporary worlds and full test reports stay under the selected game or build directory. Source-controlled JSON records summarize their relevant results; they do not replace the original logs when diagnosing a new failure.
