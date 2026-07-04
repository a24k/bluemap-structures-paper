# Requirements

Refined from [`INITIAL_PROJECT_BREAF.md`](../INITIAL_PROJECT_BREAF.md). Decisions made
during refinement are marked **[D]** with their rationale.

## 1. Goal

A Bukkit/Paper plugin that renders seed-derived structure markers (villages, strongholds,
ocean monuments, …) on [BlueMap](https://bluemap.bluecolored.de/)'s web map — the
Chunkbase-Seed-Map experience, but live on the server's own map. Functional parity with
the Fabric mod [mc-bluemap-structures](https://github.com/dannysmith/mc-bluemap-structures),
reimplemented on the Paper API so it runs on Paper/Purpur servers.

## 2. Target platform

| Item              | Decision                                                     |
| ----------------- | ------------------------------------------------------------ |
| API               | Paper API (`io.papermc.paper:paper-api`)                     |
| Version           | **[D]** `1.21.11-R0.1-SNAPSHOT` — same Minecraft version the reference mod targets, and the most likely stable line for current Purpur. Minecraft's 2026 year-based versions (26.x) are out of scope until Purpur/BlueMap support is confirmed. |
| Runtime           | Purpur (Paper-compatible); plain Bukkit/Spigot out of scope   |
| Java              | 21 (required by Paper 1.20.5+)                                |
| BlueMap           | BlueMap 5.x via `de.bluecolored:bluemap-api:2.8.0` (soft dependency — the plugin idles harmlessly when BlueMap is absent) |
| Plugin descriptor | **[D]** legacy `plugin.yml` — `paper-plugin.yml` offers no benefit here (no loader isolation needed, BlueMap is reached via its API singleton, not classpath), while `softdepend` + `api-version` are battle-tested. |

## 3. Functional requirements

### FR-1 Structure coverage

Compute, from the world seed only (no chunk loading/generation), the positions of the
20 structure layers supported by the reference mod:

village, desert pyramid, jungle temple, swamp hut, igloo, pillager outpost, ancient
city, trail ruins, trial chambers, ocean ruin, shipwreck, ruined portal (overworld),
ocean monument, woodland mansion, nether fortress, bastion remnant, ruined portal
(nether), end city, buried treasure, stronghold.

- Positions come from `World#locateNearestStructure(Location, Structure, int, boolean)`
  with `findUnexplored = true` — the same code path as vanilla `/locate`. No
  reimplementation of the placement algorithm.
- One *layer* may aggregate several registry structures (e.g. the "village" layer covers
  `minecraft:village_plains` … `village_taiga`; "ocean ruin" covers cold + warm).
- **[D]** Buried treasure ships **disabled by default**: its placement grid is 1 chunk,
  so exhaustive enumeration via `locateNearestStructure` costs ~100k queries at the
  default radius. It stays available as an opt-in with a documented warning. (The
  reference mod affords it only because it re-derives positions directly from the seed.)

### FR-2 Area enumeration

`locateNearestStructure` returns only the nearest hit, so exhaustive coverage of a
radius is achieved by sampling:

- Grid-placed structures: sample the center of every placement cell (side =
  `spacing` chunks, cell grid anchored at chunk 0,0) intersecting the search square,
  with a per-query search radius that covers the whole cell. Results are de-duplicated.
- Strongholds (concentric rings): sample along each vanilla ring that intersects the
  search radius, densely enough that every stronghold in the ring is the nearest hit of
  at least one sample.
- Search area: square of configurable half-side `radius-blocks` centered on **world
  origin (0,0)** **[D]** — matches the reference mod and the vanilla ring center;
  spawn-centered search can be added later if wanted.

### FR-3 BlueMap presentation

- One toggleable BlueMap `MarkerSet` per structure layer per map, so each layer can be
  switched on/off in the web UI sidebar.
- Each structure is a `POIMarker` with:
  - a per-layer icon (reused from mc-bluemap-structures, MIT — see
    `THIRD_PARTY_NOTICES.md`), anchored at its center;
  - a label `<display name> (x, z)`;
  - a popup containing a copyable `/tp` command (`/tp @s x y z`);
  - zoom-gated visibility (`maxDistance`) so dense layers (shipwrecks, ocean ruins…)
    appear only when zoomed in — mitigates BlueMap's many-markers slowdown.
- Markers are registered when BlueMapAPI fires `onEnable` and removed on `onDisable`,
  so BlueMap reloads are handled.
- Dimension-aware: overworld layers on overworld maps, nether layers on nether maps,
  end city on end maps.

### FR-4 Configuration (`config.yml`)

| Key                          | Default | Meaning                                        |
| ---------------------------- | ------- | ---------------------------------------------- |
| `radius-blocks`              | `5000`  | Half-side of the search square around (0,0)    |
| `scan.budget-ms-per-tick`    | `20`    | Main-thread time budget per tick for locating  |
| `scan.cache-enabled`         | `true`  | Persist scan results; skip re-scan on restart  |
| `layers.<id>`                | `true` (buried treasure: `false`) | Per-layer toggle |

Unknown layer ids are ignored with a warning; missing keys fall back to defaults.

### FR-5 Result caching

Scan output is deterministic given (seed, radius, layer set, plugin data version), so
results are cached per world in the plugin data folder and reused on restart when the
key matches. Deleting the cache or changing config triggers a re-scan.

## 4. Non-functional requirements

- **NFR-1 TPS safety.** `locateNearestStructure` runs on the main thread (its
  thread-safety off-main is undocumented), but *batched*: each tick consumes at most
  `scan.budget-ms-per-tick` milliseconds, then yields. The scan is a one-time startup
  cost (amortized to zero by FR-5). **[D]** Async execution via
  `Bukkit.getAsyncScheduler()` is deliberately deferred until the main-thread cost is
  measured (brief's "Known Concerns"); the scanner is structured so the execution
  strategy is swappable.
- **NFR-2 Web-UI responsiveness.** Dense layers get `maxDistance` zoom gating with the
  same distance tiers as the reference mod (5000 / 1000).
- **NFR-3 Testability.** All seed-independent logic (catalog, sampling plans,
  deduplication, config parsing, marker content) lives in a pure-Java `core` module
  with no Bukkit/BlueMap dependency, developed test-first (JUnit 5). The `plugin`
  module is a thin adapter.
- **NFR-4 Observability.** Scan progress (per layer counts, total queries, elapsed) is
  logged at INFO; misconfiguration at WARN.

## 5. Out of scope (v1)

- Folia support (Purpur is not Folia; classic `BukkitScheduler` is used).
- Live re-scan commands / config hot-reload (restart to apply).
- Spawn-centered or multi-center search areas.
- Biome-derived filtering beyond what `locateNearestStructure` already does (it is
  biome-exact — better than Chunkbase-style prediction).
- Minecraft 26.x support (revisit when Purpur lands there).

## 6. Open questions carried into implementation

- The `radius` parameter of `locateNearestStructure` is passed straight through to
  vanilla `findNearestMapStructure`, where it acts as a *chunk ring count* for
  random-spread placements despite the Javadoc saying blocks. The sampling plan sizes
  it in chunks, generously; verified empirically during server testing (see
  `docs/DESIGN.md` §Sampling).
- Real cost per query is unmeasured; the per-tick budget default (20 ms) is
  conservative and tunable.
