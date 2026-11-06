# Materialize variant index

`LegacyClientData.materialize` looks a stack up by its full value, then by variant (`key`, `metadata`, `tag`), because callers often build stacks with another count or an empty name. The variant lookup used to scan all of `originals` on every miss. A 120-second JFR recording of a survival world showed that scan in 20.7% of server-thread and 44.2% of render-thread Java samples. These are sample shares, not Tick or frame time.

`originalsByVariant` replaces the scan. `snapshot()` is the only writer of `originals` and nothing removes entries, so it is also the only place that maintains the index.

## Behavior difference

When several originals share a variant, the scan returned whichever came first in `HashMap` iteration order, which can change as the map grows. The index always returns the first one written. This matters only if those originals differ in content, for example old NBT whose string form omits array contents. Verify mode reports such cases.

An entry with a null `key` or `tag` made the scan throw `NullPointerException`; the index treats it as an ordinary value.

## Development switch

Set the system property `mfcl.debug.materializeIndex` (`-PmfclMaterializeIndex=<mode>` for Gradle run configurations):

| Value | Lookup used | Purpose |
| --- | --- | --- |
| unset | Index | Normal behavior |
| `verify` | Index, plus a full scan of every lookup that misses the exact entry | Finds disagreements and logs counters every 10 seconds. Far slower than either lookup, so never use it for timing. |
| `scan` | Original linear scan | Control group for A/B runs |

Verify mode counts a mismatch when the index and a full scan disagree on whether the variant exists, or when any original of that variant differs from the indexed one in item, damage or NBT. It never changes the returned stack, and it logs only the key, metadata and tag length. `ambiguousVariants` counts lookups where several originals share a variant, and `originals` and `variants` show how large the maps have grown.

## Validation

2026-11-06: `runClient -PmfclItemAudit -PmfclMaterializeIndex=verify` in the isolated game directory `build/materialize-verify-game2`, with Thaumcraft 4.2.3.5, Baubles 1.0.1.10 and Extra Utilities 1.2.12 (802 catalog variants, about one minute of audit). The log has no mismatch or check-failure lines. The last periodic report was:

| Counter | Value |
| --- | ---: |
| Exact hits | 7019 |
| Variant hits | 94983 |
| Misses | 104 |
| Entries visited by verification scans | 251869650 |
| Lookups with several originals for the variant | 63551 |
| `originals` / variants | 2826 / 1730 |

So 93.1% of lookups missed the exact entry, and two thirds of the variant hits had several candidates whose item, damage and NBT all matched the indexed original. `originals` grew from 1248 to 2826 during the run while variants grew from 1248 to 1730.

The audit itself ended with `FAILURES_FOUND`: `Thaumcraft:ItemSpawnerEgg` metadata 0 and 1 failed on stone. The cause is a null `Calendar` in the old `EntityZombie`, and no `materialize` frame appears in any logged error. The log also shows hosted Pech, Inhabited Zombie and Taint Villager entities removed after a null `VillageCollection`, and a TC4 particle bridge failure on the wrong thread. There is no earlier report with TC4 and no `scan` run with this setup, so these failures are not confirmed to predate the index.

This covers none of the wider mod pack or a long play session, and it measures no performance difference.

2026-11-06, mod pack: a copy of the "胶兽大冒险Ⅱ" instance with this build of MFCL 1.0.4 replacing its 1.0.1 jar, launched offline in `verify` mode into a copy of its survival world, about 5 minutes after joining. The log has no mismatch or check-failure lines. The last periodic report was:

| Counter | Value |
| --- | ---: |
| Exact hits | 30944 |
| Variant hits | 521907 |
| Misses | 35725 |
| Entries visited by verification scans | 3330748986 |
| Lookups with several originals for the variant | 492281 |
| `originals` / variants | 6548 / 4787 |

So 94.7% of lookups missed the exact entry, with about 5970 entries per verification scan. The old scan stopped at its first match, so its average on hits was lower. In the last four minutes these lookups ran at about 1700 per second.

`originals` jumped from 2767 to 6545 while joining the world, then gained only 3 entries in the next four minutes. This run does not show the growth during play that the code path allows: it ran no scripted item changes, so the player's inventory probably stayed mostly unchanged.

This run only stood in one part of the world, and verify mode's extra scans change its timing, so it says nothing about performance.

## Performance

2026-11-06: four runs of the same jar on the same mod pack copy, in the order `scan`, index, index, `scan`. Each reset the world, stood idle for 120 seconds, then measured for 180 seconds with vsync and the frame limit off.

| Metric | `scan` runs | Index runs | Change of means |
| --- | ---: | ---: | ---: |
| MSPT mean | 45.3 / 45.0 ms | 38.5 / 36.0 ms | −17.4% |
| MSPT median | 42.3 / 42.3 ms | 34.2 / 34.6 ms | −18.6% |
| Ticks over 50 ms | 13.4 / 18.9% | 8.7 / 3.9% | −60.7% |
| Average FPS | 48.1 / 43.5 | 65.0 / 57.8 | +34.2% |
| Frame time median | 16.2 / 18.4 ms | 10.6 / 13.4 ms | −30.6% |
| Frame time P95 | 35.1 / 42.3 ms | 31.1 / 30.2 ms | −20.8% |

`materialize` fell from about 16% of server-thread and 27% of render-thread samples to 0.3% or less. The ranges above do not overlap, but each mode has only two runs. MSPT P95 and P99, frame time P99 and monitor blocking do overlap, so this shows no reliable change in them. TPS stayed near 20 in every run. The FPS figures have no frame limit, and the runs cover standing idle rather than opening screens or moving items.

## Not addressed

- `originals` still never shrinks. Each new NBT or count value of an encoded native stack keeps another copy, although a mostly idle run showed little growth after joining.
- `inLegacy` still holds the instance lock for its whole callback.
- `itemColor` still copies the stack and calls through reflection on every invocation.
