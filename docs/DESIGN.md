# Design

> **Architecture pivot (issue #3):** the first implementation drove
> `World#locateNearestStructure(findUnexplored=true)` from the main thread. Real-server
> testing showed that call **synchronously loads chunks** to the STRUCTURE_STARTS stage
> per candidate cell (`getStructureGeneratingAt` → `syncLoadNonFull`), parking the
> server thread for 10+ seconds per call under startup load. The design below is the
> replacement: Chunkbase-style seed math with Paper-API biome validation — zero chunk
> access, async, milliseconds per world.

## 1. Module layout

```
bluemap-structures-paper/
├── core/     Pure Java (`--release 21`). No Bukkit/Paper/BlueMap types. Unit-tested (JUnit 5).
└── plugin/   Paper plugin (`--release 25`). compileOnly paper-api + bluemap-api. Thin adapter.
```

The split is driven by two forces:

1. **TDD.** Everything that can be a pure function is one, and lives in `core` where it
   is trivially testable: the structure catalog, the seed-math locator, config
   interpretation, marker text/ids.
2. **Build environment.** The sandboxed dev environment can reach Maven Central but not
   `repo.papermc.io` / `repo.bluecolored.de`, and only ships JDK 21. `core` builds and
   tests offline against Central; `plugin` compiles in GitHub Actions CI (JDK 25,
   unrestricted network). Local dev loop: `./gradlew :core:test`.

## 2. core module (`org.ykak.minecraft.bluemapstructurespaper.core`)

### 2.1 StructureCatalog / StructureLayer

Static table of the 20 supported layers. Each layer carries its vanilla placement
parameters (cross-checked against cubiomes `finders.c`, MIT) and the
`minecraft:has_structure/*` biome tag ids used for validation:

```java
record StructureLayer(
    String id, String displayName, Dimension dimension,
    Placement placement,          // see 2.2
    List<String> structureKeys,   // registry keys (metadata / cache identity)
    List<String> biomeTagIds,     // has_structure/* tags; empty = no biome restriction
    int zoomMaxDistance,          // 5000 always visible / 1000 zoomed-in only
    boolean defaultEnabled)
```

Layer set (spacing/separation/salt omitted here — the catalog source is authoritative):
village, desert_pyramid, jungle_temple, swamp_hut, igloo, pillager_outpost,
ancient_city, trail_ruins, trial_chambers, ocean_ruin, shipwreck, ruined_portal (OW),
monument, mansion, fortress, bastion, ruined_portal_nether, end_city, buried_treasure
(opt-in), stronghold.

Known deliberate deviation: `ruined_portal_nether` uses the 1.17+ config
(spacing 25 / separation 10, cubiomes `s_ruined_portal_n_117`); the reference mod's
40 / 15 is the pre-1.17 config.

### 2.2 SeedStructureLocator

Pure seed math, one method per placement kind (`Placement` is a sealed interface):

- **`Grid(spacing, separation, salt, spread[, frequency, exclusionZone])`** — vanilla
  random-spread: for every placement region `(rx, rz)` covering the search square,
  `Random(rx·341873128712 + rz·132897987541 + worldSeed + salt)`, then offset
  `nextInt(spacing − separation)` per axis (LINEAR) or the average of two rolls
  (TRIANGULAR: monument, mansion, end_city). One candidate chunk per region; block
  position is the chunk center (+8, +8). Two optional gates then filter the candidate:
  `frequency < 1` applies vanilla's `legacy_type_1` rarity reduction, and
  `exclusionZone` drops candidates within N chunks (Chebyshev) of another grid's
  placement candidates. Only `pillager_outpost` uses them (0.2 / villages·10 — see the
  closed-gap note below for the exact math).
- **`NetherComplex(role)`** — fortress and bastion share one grid
  (27 / 4 / salt 30084232); a second roll decides the occupant:
  `carverSeed = multA·chunkX ^ multB·chunkZ ^ worldSeed` (multA/multB from
  `Random(worldSeed).nextLong()` twice), `nextInt(5) < 2` → fortress, else bastion.
- **`ConcentricRings`** — strongholds: vanilla's 128-count / 8-ring geometric
  algorithm seeded with `Random(worldSeed)`. **Accuracy caveat:** vanilla nudges each
  position up to ~112 blocks toward a valid biome using world data this module
  deliberately lacks; we return the pre-nudge geometric position.
- **`PerChunkProbability(salt, p)`** — buried treasure:
  `Random(chunkX·341873128712 + chunkZ·132897987541 + seed + 10387320).nextFloat() < 0.01`
  per chunk.

The search space is a **union of `SearchArea`s** (issue #4):
`locate(layer, seed, List<SearchArea>, BiomeCheck)` sweeps each area's own
region/chunk range (work scales with the areas' sizes, not with the bounding box of
possibly far-apart areas) and dedupes candidates by position, so overlapping areas
never yield duplicates and each candidate is evaluated once. A single-radius overload
(`locate(layer, seed, radiusBlocks, check)` = one origin-centered area) keeps the
golden-vector tests and simple callers unchanged. Stronghold ring generation stays
anchored at (0,0); areas only filter which of the 128 positions are reported.

Biome validation is delegated: the locator calls `check.isValid(layer, x, z)` for
**every** in-union candidate (empty-tag layers included — the plugin's check treats
"no tags" as always-valid; the locator stays a pure placement engine).

**Verification:** golden-vector JUnit tests — expected positions generated by compiling
and running the reference implementation (trimmed, in scratchpad, not committed) for
seeds {42, 69420, −3849722879} — plus property tests (cell containment, determinism,
fortress/bastion partition of the shared grid).

Known fidelity gaps vs vanilla (documented, follow-up candidates): end cities have a
≥1008-block distance floor (the biome check masks most of it); cubiomes models 1.18+
fortresses as biome-gated rather than rolled. Both are validated against a real server
before changing.

Closed gap (2026-07): pillager outposts. Vanilla applies two extra gates after the
random-spread candidate — the `legacy_type_1` rarity gate (`Random((chunkX>>4 ^
(chunkZ>>4)<<4) ^ worldSeed)`, one discarded `nextInt()`, then `nextInt(5)==0`, i.e.
frequency 0.2; salt unused) and a 10-chunk (Chebyshev) exclusion zone around *village
placement candidates* (spacing 34 / sep 8 / salt 10387312, biome not consulted). Both
are now modeled in `Placement.Grid` (`frequency`, `exclusionZone`) and applied by
`SeedStructureLocator.locateGrid`. Field-verified on a real server world: all three
observed phantom markers failed the rarity gate and the one confirmed outpost passed
both gates; golden vectors regenerated with the gates applied.

### 2.3 Settings / AreaSpec / SearchArea

`Settings.fromMap(Map, catalog)` — search areas and per-layer toggles. Areas parse
into `AreaSpec(Center, radiusBlocks)` where `Center` is a sealed interface
(`Origin` | `Spawn` | `Fixed(x, z)`): the `spawn` center can only be resolved by the
plugin (main thread), so parsed config keeps centers symbolic; the plugin turns each
spec into a resolved `SearchArea(centerX, centerZ, radiusBlocks)` at scan setup.

- `areas`: list of `{center: origin|spawn|{x,z}, radius-blocks: N}` (radius clamped
  256…1,000,000, default 5000; missing center = origin; explicit coordinates clamped
  to ±30,000,000).
- `worlds.<name>.areas`: per-world override, replaces the default list entirely.
  World names are validated at scan time by the plugin, not at parse time.
- `radius-blocks` (top level or inside a world section): legacy sugar for one
  origin-centered area; warned and ignored when `areas` is present alongside it.

Malformed entries are skipped with warnings; an unusable list falls back to the
default (top level) or drops the override (world section). All problems are collected
into `warnings()` for the plugin to log.

### 2.4 IconSources / IconComposer

The icon pipeline's pure half (issue #2 — no bundled artwork):

- `IconSources.texturePath(layerId)` — the design artifact: a fixed table mapping each
  layer to one texture path inside the Minecraft **client jar** (stable 16×16
  item/block textures only, e.g. `assets/minecraft/textures/item/ender_eye.png` for
  strongholds; entity-texture face crops were deliberately avoided as too fragile).
  Unit test asserts every catalog layer has an entry.
- `IconComposer.compose(BufferedImage) → 22×22 TYPE_INT_ARGB` — crops vertical
  animation strips to their top frame, scales oversized frames down (nearest-neighbor,
  aspect preserved), centers the frame on a transparent 22×22 canvas (16×16 lands at
  offset (3,3), matching the previous icons' size and the (11,11) marker anchor).

### 2.5 MarkerData

Unchanged: marker/set ids (`bmsp-<layer>-<x>-<z>`, `structures-<layer>`), labels,
popup HTML with copyable `/tp` (HTML-escaped), `defaultY` per dimension (63 OW / 64
nether+end).

## 3. plugin module (`org.ykak.minecraft.bluemapstructurespaper`)

```
BlueMapStructuresPlugin (JavaPlugin)
 ├─ onEnable: saveDefaultConfig → Settings.fromMap → BlueMapAPI.onEnable/onDisable
 ├─ BlueMapAPI.onEnable  → (main thread hop) ScanCoordinator.start(api)
 └─ BlueMapAPI.onDisable → ScanCoordinator.stop(api)

ScanCoordinator.start — per world with a Dimension and a BlueMap map:
 ├─ MAIN: capture seed, resolve the world's area list (per-world override or default;
 │        spawn centers via World#getSpawnLocation), build BiomeTagCheck
 │        (world.vanillaBiomeProvider() + Registry<Biome> tags resolved to per-layer
 │         Set<Biome>; missing tags → WARN + skip; provider failure → validate-all);
 │        WARN once for worlds.<name> overrides matching no loaded world
 ├─ ASYNC (runTaskAsynchronously): SeedStructureLocator.locate per enabled layer
 │        (BiomeProvider#getBiome is documented thread-safe); log totals + elapsed;
 │        WARN when a world exceeds ~5000 markers (web-app responsiveness guard);
 │        then ClientAssetIcons.ensureIcons() — memoized, so N worlds trigger at
 │        most one asset fetch (see §5)
 └─ MAIN (runTask): MarkerPublisher.publish — generated icon PNGs uploaded via
          AssetStorage (version-keyed asset names, reused across restarts),
          MarkerSet per layer per map, POIMarker with icon anchor (11,11),
          maxDistance zoom gating, /tp popup; layers whose icon is missing fall
          back to BlueMap's default POI icon
```

Config is loaded from the user's file directly (`YamlConfiguration.loadConfiguration`)
rather than `getConfig()`: the latter merges the bundled config.yml in as defaults,
which would make the bundled `areas` key shadow a legacy user config that only sets
`radius-blocks`. `Settings` supplies all defaults itself.

- No `locateNearestStructure`, no chunk access, no structure-registry lookups.
- No result cache: recomputing at startup costs milliseconds.
- Worlds loaded after the scan (`/mv load`) are picked up on the next restart or
  `/bluemap reload` (unchanged limitation).

## 4. Build & CI

- Gradle 9.6 Kotlin DSL. No toolchain pinning: `:core` compiles with `--release 21`
  (keeps the local test loop alive on sandbox JDK 21), `:plugin` with `--release 25`
  (Minecraft 26.x servers run Java 25) — CI runs everything on JDK 25.
- `:core` — no deps beyond JUnit 5 (Central).
- `:plugin` — `compileOnly` `io.papermc.paper:paper-api:26.1.2.build.+` (repo.papermc.io;
  tracks the latest stable build for MC 26.1.2), `de.bluecolored:bluemap-api:2.8.0`
  (repo.bluecolored.de). Jar task copies core classes in (no shading of external libs).
- GitHub Actions (`.github/workflows/ci.yml`): JDK 25 + `./gradlew build` on push.
  CI is where `:plugin` compilation is actually verified.

## 5. Icons

No artwork is bundled (issue #2 — the previously bundled mc-bluemap-structures set had
undocumented provenance). Icons are generated at first startup from Mojang's own
client assets, which each server downloads directly from Mojang:

```
ClientAssetIcons.ensureIcons()   (plugin, async thread, memoized per server session)
 ├─ fast path: plugins/BlueMapStructuresPaper/icons/<mcVersion>-v<pluginVersion>/
 │  <layerId>.png all present → no network I/O (keyed by BOTH versions: a plugin
 │  update that changes the IconSources table regenerates from the cached jar)
 ├─ resolve Bukkit.getMinecraftVersion() via piston-meta.mojang.com
 │  (version_manifest_v2.json → version JSON → downloads.client.{url,sha1}; Gson,
 │  bundled with Paper)
 ├─ download the client jar once → assets/client-<mcVersion>.jar (SHA-1 verified,
 │  atomic move, kept as cache)
 └─ per layer: read IconSources.texturePath entry from the jar →
    IconComposer.compose → write the 22×22 PNG into the icons dir
```

Failure semantics: any network/IO/parse problem logs ONE warning and returns whatever
icons already exist on disk; affected markers use BlueMap's default POI icon and the
fetch is retried on the next startup. `MarkerPublisher` uploads the generated PNGs
into each map's `AssetStorage` under `bmsp-<mcVersion>-v<pluginVersion>-<layerId>.png`
— keyed by both versions so a server upgrade *or* a plugin update refreshes the
artwork despite AssetStorage's reuse-if-exists behavior (and the changed URL busts
browser caches).

## 6. Risks & mitigations

| Risk | Mitigation |
| --- | --- |
| Placement constants drift in future MC versions | catalog cross-checked against cubiomes; single source in `StructureCatalog`; golden-vector tests pin behavior |
| Stronghold positions off by ≤ ~112 blocks (no biome nudge) | documented; markers still land on the map; revisit if /tp precision matters |
| Outpost over-reporting (missing vanilla rarity gate) | RESOLVED: field-verified on a real server (3 phantom markers all failed the gate, the confirmed outpost passed); locator now models the legacy_type_1 rarity gate (frequency 0.2) and the 10-chunk village exclusion zone |
| BlueMap web app slows with many markers | zoom gating (`maxDistance`), buried treasure opt-in (thousands of markers), configurable search areas, WARN above ~5000 markers per world |
| `vanillaBiomeProvider` failures on exotic worlds | try/catch → validate-all + one WARN (Paper #9394) |
| Offline server / piston-meta unreachable → no icons | one WARN, markers fall back to BlueMap's default POI icon, fetch retried next startup; client jar + PNGs cached after first success |
| Mojang renames a texture path in a future MC version | per-layer WARN + default-icon fallback (never fatal); `IconSources` is the single table to fix |
| paper-api/bluemap-api unavailable in sandbox | module split; CI compiles plugin; core TDD offline |
