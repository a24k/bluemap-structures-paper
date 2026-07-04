# bluemap-structures-paper — Project Brief

## Development Target

- **Target API**: Paper API (`io.papermc.paper` / `paper-api`)
- **Runtime**: Purpur (fully compatible with the Paper API, so it will run without issues)
- Plain Bukkit/Spigot compatibility is out of scope. This is a homelab project, so
  implementation simplicity takes priority over broad portability.
- The core structure-lookup call (`World#locateNearestStructure`) is itself a method on
  the base Bukkit API, but it's fine to depend on Paper-specific extensions where they
  make the implementation easier — e.g. the async task scheduler
  (`Bukkit.getAsyncScheduler()`) and the modern `Structure` registry access.
- Whether to use the legacy `plugin.yml` or the newer `paper-plugin.yml` format should be
  decided early in implementation. The newer format gives cleaner lifecycle hooks, but
  check ecosystem/library support before committing to it.

## Background & Motivation

I run BlueMap on a Purpur (Paper-based) server. Similar to Chunkbase's Seed Map, I want
to show seed-derived structure markers (villages, strongholds, ocean monuments, etc.) on
BlueMap's web map.

A similar project exists — [dannysmith/mc-bluemap-structures](https://github.com/dannysmith/mc-bluemap-structures)
— but it's implemented as a **Fabric mod**, so it won't run on Paper/Purpur servers. This
project aims to reimplement equivalent functionality as a Bukkit/Paper plugin.

## Why the Bukkit/Paper Version Should Be Easier to Build

mc-bluemap-structures reimplements Minecraft's own structure-placement algorithm on the
Fabric side (essentially replicating what Chunkbase does). This is presumably because
Fabric makes it easy to reach vanilla internals directly (`ChunkGenerator`, `BiomeSource`,
etc.).

Bukkit/Paper, on the other hand, already exposes an official API that calls the exact
same logic behind the vanilla `/locate` command:

```java
StructureSearchResult locateNearestStructure(
    Location origin,
    Structure structure,
    int radius,
    boolean findUnexplored
)
```

With `findUnexplored = true`, this computes coordinates purely from the seed and vanilla's
structure-placement algorithm, without actually generating or loading chunks. In other
words, **there's no need to reimplement a Chunkbase-equivalent algorithm from scratch**,
so this version should end up with less code than the Fabric original.

Reference — relevant method in the Bukkit/Paper/Purpur Javadocs:
- `org.bukkit.World#locateNearestStructure(Location, Structure, int, boolean)`
- Legacy (deprecated): `org.bukkit.World#locateNearestStructure(Location, StructureType, int, boolean)`

## Proposed Implementation Approach

1. **Enumerate target structures**
   Get the target structure types via `RegistryAccess` → `Registry<Structure>`
   (villages, strongholds, ocean monuments, woodland mansions, ancient cities,
   pillager outposts, etc. — use the 19 structure types supported by
   mc-bluemap-structures as a reference list).

2. **Exhaustively enumerate all structures within a region**
   `locateNearestStructure` only returns the single nearest structure from a given
   origin, so getting Chunkbase-style "everything in this area" coverage requires some
   extra work:
   - Know each structure type's spacing (placement interval — e.g. villages are spaced
     ~34 chunks apart) and sample origin points on a grid slightly finer than that
     spacing.
   - De-duplicate the resulting coordinates.
   - Spacing values need to come from vanilla worldgen constants (equivalent to
     `RandomSpreadStructurePlacement` inside `StructureSet`). If these aren't exposed
     directly via the Paper API, hardcode the known values (Chunkbase itself likely
     takes a similar approach).

3. **Performance**
   `locateNearestStructure` can be expensive, so run it on Paper's async task scheduler
   (`Bukkit.getAsyncScheduler()` or similar) and only apply results to the BlueMap API
   back on the main thread. Query counts can grow quickly with large radii or many
   structure types — needs benchmarking and tuning.

4. **BlueMap integration**
   Use the BlueMap API (`de.bluecolored.bluemap.api.BlueMapAPI`) and register a separate
   marker set (layer) per structure type, so each type can be toggled independently in
   BlueMap's web UI sidebar (matching mc-bluemap-structures' UX). Markers should include
   an icon and a tooltip with a copyable `/tp` command to the coordinates.

5. **Configuration**
   A `config.yml`-style file controlling search radius (default TBD) and per-structure-type
   enable/disable toggles.

## Known Concerns / Things to Validate

- The real-world cost of `locateNearestStructure` (TPS impact, thread-safety when called
  from an async context) hasn't been measured yet. Start benchmarking with a small radius
  and a few structure types in a prototype.
- Investigate whether structure spacing/separation values are exposed anywhere in the
  Paper API — otherwise hardcode the vanilla constants.
- BlueMap's web app is known to degrade in performance with very large numbers of
  markers (noted in the mc-bluemap-structures README). The same issue is likely to show
  up here, so consider zoom-level-based display control (e.g. only show densely-packed
  structure types once zoomed in).

## Environment

- Server: Purpur (Paper-based)
- BlueMap: currently running 5.20
- Target Minecraft version: latest stable line (to be confirmed and pinned)

## Reference Links

- Original implementation (Fabric): https://github.com/dannysmith/mc-bluemap-structures
- Author's write-up: https://danny.is/notes/minecraft-bluemap-plugins/
- Alternative Paper-based structure detection (reads actually-generated chunks, no seed
  prediction): https://github.com/TechnicJelle/BlueMapStructures
- BlueMap API: https://github.com/BlueMap-Minecraft/BlueMapAPI

## Repository Name

**`bluemap-structures-paper`** — confirmed.
