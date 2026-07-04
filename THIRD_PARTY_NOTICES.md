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
