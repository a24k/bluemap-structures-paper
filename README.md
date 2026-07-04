# bluemap-structures-paper

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
- **Per-layer toggles** in the BlueMap sidebar, each with its own icon.
- **Copyable `/tp` command** in every marker popup.
- **Zoom-gated visibility** for dense layers (shipwrecks, ocean ruins, …) to keep the
  web app responsive.
- **Seed-exact, biome-exact** — positions come from the same code path as vanilla
  `/locate` (`World#locateNearestStructure` with `findUnexplored=true`), so unlike
  pure seed calculators there are no biome-mismatch false positives.
- **TPS-friendly** — the one-time scan is time-budgeted per tick (default 20 ms) and
  results are cached, so restarts are free.

## Requirements

- Paper or Purpur, Minecraft **1.21.11** (`api-version: 1.21`)
- Java 21
- [BlueMap](https://modrinth.com/plugin/bluemap) 5.x (soft dependency — the plugin
  idles when BlueMap is absent)

## Install

1. Drop `BlueMapStructuresPaper-x.y.z.jar` into `plugins/`.
2. Restart. On first start the plugin scans each mapped world (progress in console)
   and registers the marker layers when done. Subsequent restarts reuse the cache.

## Configuration

`plugins/BlueMapStructuresPaper/config.yml`:

```yaml
radius-blocks: 5000        # half-side of the scanned square around (0,0)
scan:
  budget-ms-per-tick: 20   # max ms per tick spent scanning (1-45)
  cache-enabled: true      # reuse results across restarts
layers:                    # one toggle per structure layer
  village: true
  # ...
  buried_treasure: false   # opt-in: per-chunk placement makes scanning expensive
```

After changing `radius-blocks` or `layers`, the next restart re-scans automatically
(the cache key includes them). Delete `plugins/BlueMapStructuresPaper/cache/` to force
a re-scan.

## How it works

`locateNearestStructure` only returns the *nearest* structure, so the plugin samples a
grid sized to each structure's vanilla placement spacing (one query per placement
cell), plus ring-shaped sampling for strongholds, then de-duplicates the hits. See
[docs/DESIGN.md](docs/DESIGN.md) for the details and
[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) for the refined requirements.

## Building

```sh
./gradlew build          # plugin jar at plugin/build/libs/
./gradlew :core:test     # pure-logic tests only (no Paper repo access needed)
```

## Related projects

- [mc-bluemap-structures](https://github.com/dannysmith/mc-bluemap-structures) — the
  Fabric original this plugin reimplements (icons reused under MIT, see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)).
- [BlueMapStructures](https://github.com/TechnicJelle/BlueMapStructures) — Paper-based
  alternative that reads *generated* chunks from disk instead of predicting from the
  seed.
