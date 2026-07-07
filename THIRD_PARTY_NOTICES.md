# Third-party notices

## Marker icons (Mojang client assets, not redistributed)

Since 0.4.0 the marker icons are **generated at runtime** on each server: the plugin
downloads the Minecraft client jar for the server's version directly from Mojang's
official distribution endpoint (`piston-meta.mojang.com` — the same mechanism game
launchers use), crops per-layer 22×22 icons from its item/block textures, and caches
the results under the plugin data folder. No Mojang assets are bundled in or
redistributed by this repository; each server obtains them from Mojang under its own
Minecraft EULA. When the download is unavailable (offline servers), BlueMap's default
POI icon is used instead.

Versions before 0.4.0 bundled the icon set from mc-bluemap-structures (see below);
those icons were removed because their provenance was not documented upstream
(issue #2).

## mc-bluemap-structures

- Source: https://github.com/dannysmith/mc-bluemap-structures
- Author: Danny Smith (`dannysmith`)
- License: MIT — declared in the project's `fabric.mod.json`; the repository contains
  no LICENSE file.

Reused in this project:

- The structure catalog reference data (spacing / separation / zoom-visibility tiers)
  that informed `core/src/main/java/.../core/StructureCatalog.java`
  (with corrections — see docs/DESIGN.md).

No source code was copied; this plugin is an independent implementation on the Paper
API. The project's marker icon set was bundled up to 0.3.0 and has been removed in
favor of runtime-generated icons (see above).

## Seed-math placement algorithm (provenance)

`core`'s `SeedStructureLocator` implements vanilla Minecraft's structure-placement
algorithm (random-spread region math, fortress/bastion occupancy roll, stronghold
rings, buried-treasure probability). Provenance of that implementation:

- The algorithm and its numeric constants (region-seed multipliers, per-structure-set
  salts/spacing/separation) are uncopyrightable facts of the game's behavior, publicly
  documented in multiple open-source projects.
- Constants and formulas were cross-checked against
  [cubiomes](https://github.com/Cubitect/cubiomes) (MIT, LICENSE file present) —
  no cubiomes code was ported.
- mc-bluemap-structures (above) was read as an algorithm reference, and trimmed copies
  of its locators were compiled *locally, outside the repository* to machine-generate
  the golden-vector test coordinates in
  `core/src/test/.../SeedStructureLocatorGoldenVectorTest.java`. Those coordinates are
  computed facts; the generator code is not distributed with this project.
- No Mojang code was decompiled or consulted; all game-internal knowledge came via the
  published open-source implementations above.

The implementation itself was written fresh for this project (with the references in
view — not a formal two-party clean room, noted for transparency).
