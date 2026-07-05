package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IconSourcesTest {

  @Test
  void everyCatalogLayerHasATexturePath() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      assertTrue(
          IconSources.texturePath(layer.id()).isPresent(),
          layer.id() + " has no icon texture source");
    }
  }

  @Test
  void texturePathsPointIntoClientJarItemOrBlockTextures() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      String path = IconSources.texturePath(layer.id()).orElseThrow();
      assertTrue(
          path.matches("^assets/minecraft/textures/(item|block)/[a-z0-9_]+\\.png$"),
          layer.id() + " has bad texture path " + path);
    }
  }

  @Test
  void unknownLayerIdYieldsEmpty() {
    assertTrue(IconSources.texturePath("no_such_layer").isEmpty());
    assertTrue(IconSources.texturePath("").isEmpty());
  }
}
