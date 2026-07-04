package org.ykak.minecraft.bluemapstructurespaper;

import org.ykak.minecraft.bluemapstructurespaper.core.Deduplicator;
import org.ykak.minecraft.bluemapstructurespaper.core.FoundStructure;
import org.ykak.minecraft.bluemapstructurespaper.core.SamplePlanner;
import org.ykak.minecraft.bluemapstructurespaper.core.SampleQuery;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StructureSearchResult;

/**
 * Executes the planned {@code locateNearestStructure} queries on the main thread,
 * consuming at most {@code budgetMsPerTick} milliseconds per tick (the call's
 * thread-safety off the main thread is undocumented — see docs/REQUIREMENTS.md NFR-1).
 */
final class StructureScanner extends BukkitRunnable {

  /** One structure of one layer to enumerate. */
  record LayerQuery(StructureLayer layer, String structureKey, Structure structure) {}

  private record PendingLocate(LayerQuery query, SampleQuery sample) {}

  private final Plugin plugin;
  private final World world;
  private final int budgetMsPerTick;
  private final Consumer<Map<String, List<FoundStructure>>> onComplete;

  private final ArrayDeque<PendingLocate> pending = new ArrayDeque<>();
  private final Map<String, List<FoundStructure>> foundByLayer = new LinkedHashMap<>();
  private final int totalQueries;
  private final long startedNanos = System.nanoTime();
  private boolean started;

  StructureScanner(
      Plugin plugin,
      World world,
      List<LayerQuery> queries,
      int radiusBlocks,
      int budgetMsPerTick,
      Consumer<Map<String, List<FoundStructure>>> onComplete) {
    this.plugin = plugin;
    this.world = world;
    this.budgetMsPerTick = budgetMsPerTick;
    this.onComplete = onComplete;
    for (LayerQuery query : queries) {
      foundByLayer.putIfAbsent(query.layer().id(), new ArrayList<>());
      for (SampleQuery sample : SamplePlanner.forLayer(query.layer(), radiusBlocks)) {
        pending.add(new PendingLocate(query, sample));
      }
    }
    this.totalQueries = pending.size();
  }

  void start() {
    started = true;
    runTaskTimer(plugin, 1L, 1L);
  }

  void stop() {
    if (started && !isCancelled()) {
      cancel();
    }
    pending.clear();
  }

  @Override
  public void run() {
    long deadline = System.nanoTime() + budgetMsPerTick * 1_000_000L;
    while (!pending.isEmpty() && System.nanoTime() < deadline) {
      PendingLocate next = pending.poll();
      locate(next);
    }
    if (pending.isEmpty()) {
      cancel();
      finish();
    }
  }

  private void locate(PendingLocate task) {
    SampleQuery sample = task.sample();
    Location origin = new Location(world, sample.blockX(), 0, sample.blockZ());
    StructureSearchResult result =
        world.locateNearestStructure(
            origin, task.query().structure(), sample.radiusChunks(), true);
    if (result == null) {
      return;
    }
    Location location = result.getLocation();
    foundByLayer
        .get(task.query().layer().id())
        .add(
            new FoundStructure(
                task.query().structureKey(), location.getBlockX(), location.getBlockZ()));
  }

  private void finish() {
    Map<String, List<FoundStructure>> deduped = new LinkedHashMap<>();
    int total = 0;
    for (Map.Entry<String, List<FoundStructure>> entry : foundByLayer.entrySet()) {
      List<FoundStructure> unique = Deduplicator.dedupe(entry.getValue());
      deduped.put(entry.getKey(), unique);
      total += unique.size();
    }
    long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
    plugin
        .getLogger()
        .info(
            "World '" + world.getName() + "': scan finished — " + total + " structure(s) from "
                + totalQueries + " queries in " + elapsedMs + " ms (budgeted "
                + budgetMsPerTick + " ms/tick).");
    onComplete.accept(deduped);
  }
}
