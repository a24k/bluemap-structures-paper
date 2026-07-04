# Third-party notices

## mc-bluemap-structures

- Source: https://github.com/dannysmith/mc-bluemap-structures
- Author: Danny Smith (`dannysmith`)
- License: MIT — declared in the project's `fabric.mod.json`; the repository contains
  no LICENSE file, and the original provenance of the icon artwork is not documented
  upstream. Replacing the bundled icons with runtime-fetched official client assets is
  tracked in issue #2.

Reused in this project:

- The marker icon set (`plugin/src/main/resources/icons/*.png`), copied verbatim.
- The structure catalog reference data (spacing / separation / zoom-visibility tiers)
  that informed `core/src/main/java/dev/a24k/bluemapstructures/core/StructureCatalog.java`
  (with corrections — see docs/DESIGN.md).

No source code was copied; this plugin is an independent implementation on the Paper
API.

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
