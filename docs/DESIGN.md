# Design

## 1. Module layout

```
bluemap-structures-paper/
├── core/     Pure Java 21. No Bukkit/Paper/BlueMap types. Unit-tested (JUnit 5).
└── plugin/   Paper plugin. compileOnly paper-api + bluemap-api. Thin adapter over core.
```

The split is driven by two forces:

1. **TDD.** Everything that can be a pure function is one, and lives in `core` where it
   is trivially testable: the structure catalog, sampling plans, deduplication, config
   interpretation, marker text/ids, cache keys.
2. **Build environment.** The sandboxed dev environment can reach Maven Central but not
   `repo.papermc.io` / `repo.bluecolored.de`. `core` builds and tests offline against
   Central only; `plugin` compiles in GitHub Actions CI (unrestricted network). Local
   dev loop: `./gradlew :core:test`.

## 2. core module (`org.ykak.minecraft.bluemapstructurespaper.core`)

### 2.1 StructureCatalog / StructureLayer

Static table of the 20 supported layers, ported from mc-bluemap-structures'
`StructureType` (MIT; see `THIRD_PARTY_NOTICES.md`) and cross-checked against vanilla
`StructureSet` constants:

```java
record StructureLayer(
    String id,                 // "village" — config key & marker-set id suffix
    String displayName,        // "Villages"
    Dimension dimension,       // OVERWORLD | NETHER | END
    Placement placement,       // GRID(spacingChunks) | CONCENTRIC_RINGS
    List<String> structureKeys,// registry keys aggregated into this layer
    int zoomMaxDistance,       // POIMarker maxDistance (5000 wide / 1000 zoomed-in)
    String iconFile,           // classpath icons/<file>.png
    boolean defaultEnabled)    // buried_treasure: false
```

| layer id | keys (minecraft:…) | spacing (chunks) | dim | zoom |
| --- | --- | --- | --- | --- |
| village | village_plains, village_desert, village_savanna, village_snowy, village_taiga | 34 | OW | 5000 |
| desert_pyramid | desert_pyramid | 32 | OW | 5000 |
| jungle_temple | jungle_pyramid | 32 | OW | 5000 |
| swamp_hut | swamp_hut | 32 | OW | 5000 |
| igloo | igloo | 32 | OW | 5000 |
| pillager_outpost | pillager_outpost | 32 | OW | 5000 |
| ancient_city | ancient_city | 24 | OW | 5000 |
| trail_ruins | trail_ruins | 34 | OW | 5000 |
| trial_chambers | trial_chambers | 34 | OW | 1000 |
| ocean_ruin | ocean_ruin_cold, ocean_ruin_warm | 20 | OW | 1000 |
| shipwreck | shipwreck, shipwreck_beached | 24 | OW | 1000 |
| ruined_portal | ruined_portal, ruined_portal_desert, ruined_portal_jungle, ruined_portal_swamp, ruined_portal_mountain, ruined_portal_ocean | 40 | OW | 1000 |
| monument | monument | 32 | OW | 5000 |
| mansion | mansion | 80 | OW | 5000 |
| fortress | fortress | 27 | NETHER | 5000 |
| bastion | bastion_remnant | 27 | NETHER | 5000 |
| ruined_portal_nether | ruined_portal_nether | 25 | NETHER | 5000 |
| end_city | end_city | 20 | END | 5000 |
| buried_treasure | buried_treasure | 1 | OW | 1000 |
| stronghold | stronghold | rings | OW | 5000 |

Note: `jungle_temple`'s registry key really is `jungle_pyramid`; the Bukkit `Structure`
registry follows vanilla naming. `ruined_portal_nether` uses vanilla's spacing 25 —
the reference mod's 40 would leave placement cells unsampled.

### 2.2 Sampling — SamplePlanner

`locateNearestStructure(origin, structure, radius, findUnexplored=true)` returns the
single nearest instance, so exhaustive coverage = many well-placed queries + dedup.

**Grid placements** (`SamplePlan.forGrid`). Vanilla random-spread placement puts at most
one attempt in each `spacing × spacing`-chunk cell, cells anchored at chunk (0,0). For
every cell intersecting the search square we emit one sample at the cell's block center.
Per-query search radius (in *chunks* — see §2.5) is `spacing`, which from the cell
center covers the entire cell regardless of where in the cell the attempt landed.
Neighboring queries overlap on purpose; `Deduplicator` collapses them.

Query count ≈ `ceil(2r / 16·spacing)²` per layer — at r=5000: village ~100, mansion ~16,
ocean_ruin ~250. Total for all default layers ≈ 1.6k queries, a few minutes of budgeted
main-thread time worst-case, once per world (then cached).

**Concentric rings** (`SamplePlan.forRings`). Strongholds: 128 in 8 rings (vanilla
`ConcentricRingsStructurePlacement`: distance 32, spread 3, count 128). Ring *i*
(1-based, counts 3/6/10/15/21/28/36/9) spans radius `(1280 + 3072(i−1)) … (2816 +
3072(i−1))` blocks. For each ring intersecting the search radius we emit `2 × count`
samples evenly spaced on the ring's mid-radius circle, each with a search radius wide
enough to cover its ring segment. 2× oversampling guarantees every stronghold is the
nearest hit of some sample (angular gap between samples < minimal angular spacing of
strongholds in that ring).

### 2.3 Deduplication

`Deduplicator.dedupe(List<FoundStructure>)` — identity is `(structureKey, x, z)`.
Insertion order preserved (stable output → stable cache diffs, stable marker ids).

### 2.4 Settings

`Settings.fromMap(Map<String, Object>, StructureCatalog)` parses the already-YAML-parsed
config tree (the plugin passes `FileConfiguration#getValues(true)`-style nested maps, so
core never sees Bukkit types). Defaults per REQUIREMENTS FR-4, values clamped
(`radius-blocks` 256…1_000_000, `budget-ms-per-tick` 1…45), unknown layer ids collected
into `warnings()` for the plugin to log.

### 2.5 The radius parameter caveat

CraftBukkit forwards `radius` untouched into vanilla
`ChunkGenerator#findNearestMapStructure`, where random-spread search iterates placement
cells in rings — effectively a *chunk* radius — although the Bukkit Javadoc says blocks.
We size per-query radii in chunks (grid: `spacing`; rings: ring half-width in chunks).
If an implementation ever treats it as blocks, the searched area only shrinks for values
> 16 — mitigated by the 2× ring oversampling and by grid cells being re-covered from
all 4 neighboring samples' overlap. Verified empirically on a real server (task in PR
description).

### 2.6 Marker content — MarkerData

Pure formatting: marker id `bmsp-<layer>-<x>-<z>`, marker-set id `structures-<layer>`,
label `"<display name> (x, z)"`, popup HTML with copyable `/tp @s <x> <y> <z>` in a
`<code>` block. All interpolated strings HTML-escaped.

### 2.7 Cache key — ScanCacheKey

`(worldUid, seedHash, radiusBlocks, sorted enabled layer ids, CACHE_VERSION)` →
deterministic string. Seed is stored as `SHA-256(seed)` — the cache file lives in the
server directory, but avoiding a plaintext seed copy costs nothing. Cache payload is
the found-structure list per layer, serialized with Gson (bundled with Paper).

## 3. plugin module (`org.ykak.minecraft.bluemapstructurespaper`)

```
BlueMapStructuresPlugin (JavaPlugin)
 ├─ onEnable: saveDefaultConfig → Settings.fromMap → register BlueMapAPI.onEnable/onDisable
 ├─ BlueMapAPI.onEnable  → ScanCoordinator.start(api)
 └─ BlueMapAPI.onDisable → MarkerPublisher.removeAll(api); cancel scan task

ScanCoordinator
 ├─ per Bukkit world (matched to catalog Dimension via World.Environment)
 │   ├─ cache hit  → publish immediately
 │   └─ cache miss → StructureScanner (BukkitRunnable, runTaskTimer period 1)
 │        each tick: pop sample queries until budget-ms exhausted;
 │        world.locateNearestStructure(loc, structure, radiusChunks, true)
 │        on queue drained: dedupe → cache store → publish
 └─ resolves layer structureKeys via RegistryAccess.registryAccess()
        .getRegistry(RegistryKey.STRUCTURE).get(NamespacedKey) — unknown keys logged
        & skipped (forward/backward compat)

MarkerPublisher
 ├─ icons: classpath icons/*.png → BufferedImage → api.getWebApp().createImage()
 ├─ for each BlueMapMap of api.getWorld(bukkitWorld): MarkerSet per enabled layer
 │    (label = displayName, defaultHidden = false)
 └─ POIMarker.builder().position(x, y, z).icon(addr, anchor 11,11)
        .maxDistance(zoomMaxDistance).detail(popup html)
```

- Marker publishing happens inside the scan-completion tick (main thread); BlueMap's
  marker API is safe to call there and updates are pushed to the web app by BlueMap.
- Y coordinate for markers/`/tp`: overworld & end use the located block's Y if the
  search result provides one, else sea level 63 (OW) / 64 (END); nether uses 64 to
  avoid teleporting onto the roof. Kept in core (`MarkerData.defaultY`) for testing.
- If BlueMap is not installed, `BlueMapAPI.onEnable` never fires; the plugin logs one
  INFO line and does nothing else.

## 4. Build & CI

- Gradle 9.6 Kotlin DSL. No toolchain pinning: `:core` compiles with `--release 21`
  (keeps the local test loop alive on sandbox JDK 21), `:plugin` with `--release 25`
  (Minecraft 26.x servers run Java 25) — CI runs everything on JDK 25.
- `:core` — no deps beyond JUnit 5 (Central). `./gradlew :core:test` works offline-ish
  (Central + services.gradle.org reachable in the sandbox).
- `:plugin` — `compileOnly` `io.papermc.paper:paper-api:26.1.2.build.+` (repo.papermc.io;
  tracks the latest stable build for MC 26.1.2), `de.bluecolored:bluemap-api:2.8.0`
  (repo.bluecolored.de; requires JVM 25+, hence the Java 25 module),
  `com.google.code.gson` (provided by Paper at runtime). Jar task copies core classes in
  (no shading of external libs needed).
- GitHub Actions (`.github/workflows/ci.yml`): JDK 21 + `./gradlew build` on push/PR.
  This is where `:plugin` compilation is actually verified — treat CI as part of the
  red/green loop for the plugin module.

## 5. Icons

21 PNGs (20 layers + `end_ship.png` reserved) copied from mc-bluemap-structures
(`src/main/resources/icons/`, MIT license) into `plugin/src/main/resources/icons/`.
Attribution in `THIRD_PARTY_NOTICES.md`.

## 6. Risks & mitigations

| Risk | Mitigation |
| --- | --- |
| `locateNearestStructure` cost unknown | per-tick ms budget; one-time scan; cache; radius default 5000 (not 10k) |
| radius param semantics (blocks vs chunks) | sized in chunks + overlap/oversampling (§2.5); empirical check on live server |
| BlueMap marker count vs web-app perf | zoom gating (`maxDistance`), dense layers gated at 1000 |
| paper-api/bluemap-api unavailable in sandbox | module split; CI compiles plugin; core TDD offline |
| Purpur moves to a newer MC version | single pinned constant in `gradle/libs.versions.toml` (`26.1.2.build.+`); `api-version: '26.1'` declares the 26.1+ floor |
