# Research table GUI replay performance

The original TC4 research table is captured and replayed by `LegacyGuiRenderer`; TC4: Modern does not replace its layout or controller.

Consecutive normal text commands now share the host font buffer. Text is flushed before the next non-text command, and tooltip commands still flush individually to preserve background/glyph order. Consecutive mesh primitives share an upload only when their effective texture/vertex format and captured render state match. Primitives are never sorted across text, item, texture or depth boundaries. GUI topology conversion emits quad indices directly instead of allocating an `int[][]` per primitive. A failed mesh replay also releases its unfinished host buffer.

## Local acceptance, 2026-10-30

Original Thaumcraft 4.2.3.5 + Baubles 1.0.1.10, Forge 47.4.28, Minecraft 1.20.1, JDK 17. The opt-in fixture discovers every registered aspect; the visible research palette contains 25 icons and 120 text commands. Each replay measurement uses 30 warm-up frames and 100 samples of the same captured command list.

| GUI replay CPU time | Before | After |
| --- | ---: | ---: |
| Median | 1.0414 ms | 0.4442 ms |
| P95 | 1.3087 ms | 0.7202 ms |

Median replay time decreased by about 57%. These are CPU submission times for GUI replay, not total frame times or GPU timings, and do not measure legacy capture, addon-heavy aspect registries or other hardware. Two unrelated commands differed between captures; both contained the same 25 primitives, 100 vertices and 120 text commands. The stable research panel crop (x=185..524, y=65..319 at 854x480) matched exactly: 0 different pixels out of 86,700. Notifications and the surrounding world were excluded from that comparison.

`LegacyGuiGeometryTest` passed all three tests, including comparison with the previous topology conversion for all supported modes and 0..64 vertices, texture/format/state barriers, and invalid-mode rejection. Both in-game device runs reported `PASS`, covering aspect combination and persistence, repeated clicks, native note insertion, drag release, erase, synchronization, duplication, reveal and learning. Existing unresolved translation keys in the broader device report are unrelated to this change.

## Reproduce

Prepare a disposable game directory with original TC4/Baubles inputs and the verified legacy runtime, then run:

```powershell
.\gradlew.bat --project-cache-dir build/research-gui-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/research-gui-validation' `
  test --tests github.com.gengyoubo.mfcl.client.LegacyGuiGeometryTest runClient `
  '-PmfclRunDir=build/research-gui-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclDeviceSmokeTest -PmfclGuiPerformanceTest
```

Complete Minecraft's accessibility onboarding once in a fresh test directory. The probe then creates its own disposable world and stops its own client. Measurements are stored under `researchGuiPerformance` in `logs/mfcl/device-smoke.json`; the replay screenshot is `screenshots/research-gui-performance.png`. `mfclGuiPerformanceTest` modifies only the device test fixture and has no effect on ordinary gameplay.
