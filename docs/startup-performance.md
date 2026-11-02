# Legacy startup timing and resource cache

MFCL writes `logs/mfcl/startup-timings.json` after legacy content installation and updates it when the client reaches the title screen. It includes JVM uptime, elapsed time from MFCL entry, discovery and runtime verification, embedded boot and the real old Forge lifecycle stages, content capture, base-pack generation or reuse, and the dynamic pack/recipe pass. Cache fingerprint and validation timings are included in `resourcePackBase`; phase times overlap and must not be added together. JVM uptime is not a pure MFCL measurement. Server or failed client startup may only have the earlier content-installation report.

The reusable base under `mfcl/cache/resources` is keyed by the ordered legacy classpath JAR SHA-256 values, resource-generator bytecode, captured block/item model inputs and cuboids, original icons and their generated pixels/metadata, mining tags and resource names. Unsupported input paths disable caching for that startup. Creative contents, item tint colours, recipes, tags, dimensions, potion assets and other runtime-dependent output remain in a separate per-session overlay. Both layers are presented as one resource pack; the overlay wins on duplicate resource names. The complete marker is written after generation, and a separately published pointer selects a complete immutable base. Concurrent or interrupted generation cannot publish an incomplete base. Missing files, mismatched file sizes, invalid metadata and unavailable caches cause a fresh build or session-only fallback. The cache is derived data and does not alter original JARs. Existing checksum verification and the original legacy Forge lifecycle still run; class transformation and JAR verification are not skipped.

The cache validates listed file presence and size, not a cryptographic digest of every generated output file on every hit. Manual same-length edits inside the cache may therefore require removal of the cache while the game is stopped. Old complete bases and session directories are not automatically pruned.

## Local measurements

On 2026-11-02, the disposable Forge 1.20.1 / Java 17 development client used TC4 4.2.3.5, Baubles and Extra Utilities, with 101 legacy blocks, 233 items, 627 crafting and 11 smelting recipes. One miss and two successive hits used the same build and game directory. Earlier baseline startup was substantially colder, so it is not directly comparable to the warm runs below. Results vary with OS filesystem cache, hardware and installed mods.

| Stage | Miss | Hit 1 | Hit 2 |
| --- | ---: | ---: | ---: |
| Base resource pack | 5.63 s | 0.62 s | 0.65 s |
| MFCL entry to test title screen | 24.68 s | 20.15 s | 19.76 s |

The base stage includes fingerprinting and validation; these samples are not a controlled cold-disk benchmark. All three reached the title screen and parsed all recipes. A separate run with a damaged test-cache file regenerated the base and reached the title screen. The host startup probe and cache tests cover pack composition, missing/incomplete cache entries and overlay priority; they do not prove compatibility with every mod or configuration.
