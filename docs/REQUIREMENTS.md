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
| Version           | **[D]** Minecraft **26.1 and later** (`api-version: '26.1'`), built against `paper-api 26.1.2.build.+` (latest stable build for 26.1.2 — the version the target homelab Purpur runs). 1.21.x and older are out of scope. |
| Runtime           | Purpur (Paper-compatible); plain Bukkit/Spigot out of scope   |
| Java              | 25 (required by Minecraft 26.x). The `core` module stays at Java 21 bytecode so its tests run in sandboxed dev environments that only ship JDK 21. |
| BlueMap           | BlueMap 5.x via `de.bluecolored:bluemap-api:2.8.0` (soft dependency — the plugin idles harmlessly when BlueMap is absent) |
| Plugin descriptor | **[D]** legacy `plugin.yml` — `paper-plugin.yml` offers no benefit here (no loader isolation needed, BlueMap is reached via its API singleton, not classpath), while `softdepend` + `api-version` are battle-tested. |
| Package           | **[D]** `org.ykak.minecraft.bluemapstructurespaper` — reverse-domain of the owner's `ykak.org`, grouping Minecraft projects; official plugin name **BlueMap Structures Paper**. |

## 3. Functional requirements

### FR-1 Structure coverage

Compute, from the world seed only (no chunk loading/generation), the positions of the
20 structure layers supported by the reference mod:

village, desert pyramid, jungle temple, swamp hut, igloo, pillager outpost, ancient
city, trail ruins, trial chambers, ocean ruin, shipwreck, ruined portal (overworld),
ocean monument, woodland mansion, nether fortress, bastion remnant, ruined portal
(nether), end city, buried treasure, stronghold.

Plus, beyond reference-mod parity: **mineshaft** (issue #7) — one layer aggregating
`minecraft:mineshaft` + `minecraft:mineshaft_mesa`, placed by vanilla's per-chunk
`legacy_type_3` carver-seed roll (probability 0.004).

- **[D — revised after real-server testing, see issue #3]** Positions are computed by
  reimplementing vanilla's placement math in `core` (Chunkbase-style), validated
  against biome data through the Paper API (`vanillaBiomeProvider` + `has_structure/*`
  biome tags). The originally planned `World#locateNearestStructure` path was
  disproven in practice: despite `findUnexplored = true` it synchronously loads chunks
  to the STRUCTURE_STARTS stage per candidate cell and stalls the main thread; it is
  no longer used.
- One *layer* may aggregate several registry structures (e.g. the "village" layer covers
  `minecraft:village_plains` … `village_taiga`; "ocean ruin" covers cold + warm).
- **[D]** Buried treasure ships **disabled by default**: per-chunk placement yields
  ~0.01 × chunks ≈ thousands of markers at the default radius, which strains the
  BlueMap web app. (Compute cost is no longer a concern with seed math.)
- **[D]** Mineshaft likewise ships **disabled by default** for the same reason:
  ~0.004 × chunks ≈ 1500+ markers at the default radius 5000. Zoom-gated (1000)
  when enabled.

### FR-2 Area enumeration

Exhaustive coverage of the search area comes directly from the placement math (one
candidate per placement region), not from sampling:

- Grid-placed structures: vanilla random-spread — one candidate chunk per
  `spacing`-chunk region, position derived from
  `Random(regionX·c1 + regionZ·c2 + seed + salt)`; fortress/bastion share one grid
  with a weighted occupancy roll.
- Strongholds: vanilla's concentric-ring geometry (pre-biome-nudge approximation,
  documented accuracy ≤ ~112 blocks).
- Buried treasure: per-chunk probability roll (opt-in layer).
- Search area: a configurable **list of square areas** (union semantics, deduplicated),
  each `(center, radius-blocks)` where center is `origin`, `spawn` (resolved per world
  at scan setup) or explicit `{x, z}` block coordinates. A per-world `worlds.<name>.areas`
  override replaces the default list entirely for that world. **[D — revised, issue #4]**
  The original single origin-centered square remains the default (`areas: [{center:
  origin, radius-blocks: 5000}]`); real-server measurements (7–257 ms per world at
  radius 5000, async) showed scan time is not the limit — marker count in the web app
  is. Stronghold rings stay anchored at (0,0) by vanilla definition; areas only filter
  which results are shown.

### FR-3 BlueMap presentation

- One toggleable BlueMap `MarkerSet` per structure layer per map, so each layer can be
  switched on/off in the web UI sidebar.
- Each structure is a `POIMarker` with:
  - a per-layer icon, anchored at its center — **[D — revised, issue #2]** generated
    at first startup from Mojang's client assets (fetched by the server itself via
    `piston-meta.mojang.com`, cached in the plugin data folder) instead of bundling
    third-party artwork of undocumented provenance; when the fetch fails (offline
    server) markers use BlueMap's default POI icon;
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
| `areas`                      | `[{center: origin, radius-blocks: 5000}]` | Default search areas: list of `center` (`origin` \| `spawn` \| `{x, z}`) + `radius-blocks` (half-side, 256…1,000,000) |
| `worlds.<name>.areas`        | —       | Per-world override; replaces the default list entirely for that world |
| `radius-blocks`              | —       | Legacy sugar for `areas: [{center: origin, radius-blocks: N}]`; warned & ignored if `areas` is also set |
| `layers.<id>`                | `true` (buried treasure, mineshaft: `false`) | Per-layer toggle |

Unknown layer ids are ignored with a warning; missing keys fall back to defaults.
World names under `worlds` are checked at scan time (not parse time): overrides that
match no loaded world log a warning and are unused.

### FR-5 Recompute at startup

Seed math costs milliseconds per world, so results are recomputed on every startup /
BlueMap reload; there is no persistent cache and no state in the plugin data folder
beyond `config.yml`. (The former JSON result cache and its invalidation machinery were
removed with the pivot.)

## 4. Non-functional requirements

- **NFR-1 TPS safety.** The scan performs **no chunk or world access**: candidate
  generation is pure math and biome validation uses the noise-based
  `vanillaBiomeProvider` (documented thread-safe). It runs on an async task; the main
  thread is used only to set up per-world inputs and to publish markers. Measured
  cost target: milliseconds per world.
- **NFR-2 Web-UI responsiveness.** Dense layers get `maxDistance` zoom gating with the
  same distance tiers as the reference mod (5000 / 1000). A WARN is logged when a
  world's scan exceeds ~5000 markers, pointing at area size and layer toggles.
- **NFR-3 Testability.** All seed-independent logic (catalog, sampling plans,
  deduplication, config parsing, marker content) lives in a pure-Java `core` module
  with no Bukkit/BlueMap dependency, developed test-first (JUnit 5). The `plugin`
  module is a thin adapter.
- **NFR-4 Observability.** Scan progress (per layer counts, total queries, elapsed) is
  logged at INFO; misconfiguration at WARN.

## 5. Out of scope (v1)

- Folia support (Purpur is not Folia; classic `BukkitScheduler` is used).
- Live re-scan commands / config hot-reload (restart to apply).
- End-city ship detection (the reference mod marks end ships separately; ours marks
  the city only).
- Minecraft 1.21.x and older (the plugin targets the 26.x line onward).

## 6. Open questions carried into implementation

Fidelity gaps of the seed-math locator vs vanilla, to be checked against a real server
(`/locate` spot checks) before deciding whether to model them (see DESIGN §2.2):

- Stronghold positions are the pre-biome-nudge geometry (≤ ~112 blocks off).
- Pillager outposts — RESOLVED (2026-07): the predicted over-reporting was confirmed
  on a real server (three phantom markers all failed vanilla's `nextInt(5)==0` rarity
  gate; the one confirmed outpost passed). The locator now models the rarity gate and
  the 10-chunk village exclusion zone (see DESIGN §2.2).
- Fortress/bastion occupancy: modeled as the classic 2-of-5 carver-seed roll; cubiomes
  treats 1.18+ fortresses as biome-gated instead.
- End cities: vanilla enforces a ≥1008-block distance floor; our biome check masks
  most of it but not exactly.
- Mineshafts (issue #7): the legacy_type_3 roll is desk-verified against cubiomes
  (`getMineshafts`, and `isViableStructurePos` confirms no extra gates), but the
  real-server spot check (`/locate structure minecraft:mineshaft`, incl. one absence
  check) is still pending — the sandbox has no server to run it on.
