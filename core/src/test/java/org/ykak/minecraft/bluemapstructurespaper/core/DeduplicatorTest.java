package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeduplicatorTest {

  @Test
  void collapsesIdenticalFindsAndKeepsFirstSeenOrder() {
    FoundStructure a = new FoundStructure("minecraft:monument", 160, -320);
    FoundStructure b = new FoundStructure("minecraft:monument", 800, 480);
    FoundStructure aAgain = new FoundStructure("minecraft:monument", 160, -320);

    assertEquals(List.of(a, b), Deduplicator.dedupe(List.of(a, b, aAgain, a)));
  }

  @Test
  void samePositionDifferentStructureIsKept() {
    FoundStructure cold = new FoundStructure("minecraft:ocean_ruin_cold", 64, 64);
    FoundStructure warm = new FoundStructure("minecraft:ocean_ruin_warm", 64, 64);

    assertEquals(List.of(cold, warm), Deduplicator.dedupe(List.of(cold, warm)));
  }

  @Test
  void emptyInputYieldsEmptyOutput() {
    assertEquals(List.of(), Deduplicator.dedupe(List.of()));
  }
}
