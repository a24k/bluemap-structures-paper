package dev.a24k.bluemapstructures;

import de.bluecolored.bluemap.api.BlueMapAPI;
import dev.a24k.bluemapstructures.core.Settings;
import dev.a24k.bluemapstructures.core.StructureCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlueMapStructuresPlugin extends JavaPlugin {

  private ScanCoordinator coordinator;
  private Consumer<BlueMapAPI> apiEnableListener;
  private Consumer<BlueMapAPI> apiDisableListener;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    Settings settings = Settings.fromMap(toMap(getConfig()), StructureCatalog.layers());
    settings.warnings().forEach(warning -> getLogger().warning("config.yml: " + warning));

    coordinator = new ScanCoordinator(this, settings);

    // BlueMap may enable/disable at any point (including before us, or on /bluemap
    // reload); the listeners below are the only integration entry points. They can be
    // called from BlueMap's own threads, so hop onto the main thread for Bukkit work.
    apiEnableListener =
        api ->
            Bukkit.getScheduler()
                .runTask(
                    this,
                    () -> {
                      getLogger().info("BlueMap detected, planning structure scan...");
                      coordinator.start(api);
                    });
    apiDisableListener = api -> coordinator.stop(api);

    BlueMapAPI.onEnable(apiEnableListener);
    BlueMapAPI.onDisable(apiDisableListener);

    if (BlueMapAPI.getInstance().isEmpty()) {
      getLogger().info("Waiting for BlueMap to enable (soft dependency).");
    }
  }

  @Override
  public void onDisable() {
    if (apiEnableListener != null) {
      BlueMapAPI.unregisterListener(apiEnableListener);
    }
    if (apiDisableListener != null) {
      BlueMapAPI.unregisterListener(apiDisableListener);
    }
    if (coordinator != null) {
      BlueMapAPI.getInstance().ifPresent(coordinator::stop);
    }
  }

  /** Core's Settings parser works on plain maps; unwrap Bukkit's ConfigurationSection. */
  private static Map<String, Object> toMap(ConfigurationSection section) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (String key : section.getKeys(false)) {
      Object value = section.get(key);
      map.put(key, value instanceof ConfigurationSection sub ? toMap(sub) : value);
    }
    return map;
  }
}
