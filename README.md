# BlueMap Structures Paper

Chunkbase-style, seed-derived structure markers for [BlueMap](https://bluemap.bluecolored.de/),
as a **Paper/Purpur plugin**. Villages, strongholds, ocean monuments and 17 other
structure layers appear as toggleable marker layers on your web map — computed from the
world seed, without loading or generating chunks.

A Paper-API reimplementation of the Fabric mod
[mc-bluemap-structures](https://github.com/dannysmith/mc-bluemap-structures)
(which cannot run on Paper/Purpur servers).

## Features

- **20 structure layers** — village, desert pyramid, jungle temple, swamp hut, igloo,
  pillager outpost, ancient city, trail ruins, trial chambers, ocean ruin, shipwreck,
  ruined portal (overworld & nether), ocean monument, woodland mansion, nether
  fortress, bastion remnant, end city, buried treasure (opt-in), stronghold.
- **Per-layer toggles** in the BlueMap sidebar, each with its own icon — generated on
  first startup from Mojang's official client assets (downloaded by your server
  directly from Mojang, cached in the plugin data folder; no third-party artwork is
  bundled or redistributed).
- **Copyable `/tp` command** in every marker popup.
- **Zoom-gated visibility** for dense layers (shipwrecks, ocean ruins, …) to keep the
  web app responsive.
- **Seed-derived, biome-validated** — candidate positions come from vanilla's own
  placement math (same approach as Chunkbase), then each candidate is checked against
  the world's noise-based biome source (`has_structure/*` biome tags) through the
  Paper API. No chunks are loaded or generated at any point.
- **TPS-friendly** — the whole scan is pure math on an async thread and finishes in
  milliseconds per world; the main thread only publishes the markers.

## Requirements

- Paper or Purpur, Minecraft **26.1 or later** (`api-version: '26.1'`; built against
  paper-api 26.1.2)
- Java 25
- [BlueMap](https://modrinth.com/plugin/bluemap) 5.x (soft dependency — the plugin
  idles when BlueMap is absent)

## Install

1. Drop `BlueMapStructuresPaper-x.y.z.jar` into `plugins/`.
2. Restart. On every start the plugin recomputes the markers from each mapped world's
   seed (milliseconds per world; totals in console) and registers the marker layers.

On the first start the plugin also downloads the Minecraft client jar for your server's
version from Mojang (`piston-meta.mojang.com`, the same source launchers use) and crops
the marker icons out of its textures; both the jar and the generated icons are cached
under `plugins/BlueMapStructuresPaper/`. If the download fails (offline server),
markers still work with BlueMap's default POI icon and the plugin retries on the next
start.

## Configuration

`plugins/BlueMapStructuresPaper/config.yml`:

```yaml
areas:                     # scanned squares (union of all entries, deduplicated)
  - center: origin         # origin (0,0) | spawn | {x: <block>, z: <block>}
    radius-blocks: 5000    # half-side of the square

#worlds:                   # per-world overrides (replace the default list entirely)
#  resource:
#    areas:
#      - center: spawn
#        radius-blocks: 2000

layers:                    # one toggle per structure layer
  village: true
  # ...
  buried_treasure: false   # opt-in: ~0.01/chunk → thousands of markers at radius 5000
```

The legacy top-level `radius-blocks: 5000` form still works as shorthand for a single
origin-centered area.

Markers are recomputed from the seed on every start (it costs milliseconds), so config
changes apply on the next restart — there is no cache to clear.

## How it works

Structure positions are computed from the world seed with vanilla's placement
algorithm (random-spread grids per structure set, concentric rings for strongholds),
then validated against the server's noise-based biome source via the Paper API — the
same technique as Chunkbase's Seed Map, and the reason no chunks need to exist. An
earlier prototype used `World#locateNearestStructure`; it turned out to sync-load
chunks per candidate cell and stall the server (see issue #3). See
[docs/DESIGN.md](docs/DESIGN.md) for the details and
[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) for the refined requirements.

## Building

```sh
./gradlew build          # plugin jar at plugin/build/libs/
./gradlew :core:test     # pure-logic tests only (no Paper repo access needed)
```

## Related projects

- [mc-bluemap-structures](https://github.com/dannysmith/mc-bluemap-structures) — the
  Fabric original this plugin reimplements (structure-catalog reference data, see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)).
- [BlueMapStructures](https://github.com/TechnicJelle/BlueMapStructures) — Paper-based
  alternative that reads *generated* chunks from disk instead of predicting from the
  seed.
