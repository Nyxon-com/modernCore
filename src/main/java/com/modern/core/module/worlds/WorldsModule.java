//made with love by nyxon

package com.modern.core.module.worlds;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

public final class WorldsModule implements Module {

  private final ModernCorePlugin plugin;
  private WorldCatalog catalog;
  private SpawnService spawns;
  private WeatherService weather;
  private TimeService time;
  private WorldsListener listener;

  public WorldsModule(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String id() {
    return "worlds";
  }

  @Override
  public void enable() {
    this.catalog = new WorldCatalog(this.plugin);
    this.spawns = new SpawnService(this.plugin);
    this.weather = new WeatherService(this.plugin);
    this.time = new TimeService(this.plugin);
    this.catalog.loadConfigured();
    this.catalog.syncAll();
    this.catalog.syncFromMultiverseLater();
    this.weather.applyCatalogAll();
    this.weather.applyAll();
    this.weather.start();
    this.time.start();
    this.listener = new WorldsListener(this.plugin, this.catalog, this.spawns, this.weather, this.time);
    Bukkit.getPluginManager().registerEvents(this.listener, this.plugin);

    if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
      this.plugin.getLogger().info("Multiverse-Core rilevato: pvp, difficulty, gamemode, hunger e flight restano quelli di MV.");
    }
  }

  @Override
  public void disable() {
    if (this.weather != null) {
      this.weather.stop();
    }
    if (this.time != null) {
      this.time.stop();
    }
    if (this.listener != null) {
      HandlerList.unregisterAll(this.listener);
    }
  }

  @Override
  public void reload() {
    this.weather.stop();
    this.time.stop();
    for (org.bukkit.World world : Bukkit.getWorlds()) {
      this.catalog.apply(world);
    }
    this.weather.applyCatalogAll();
    this.weather.applyAll();
    this.weather.start();
    this.time.start();
  }

  public SpawnService spawns() {
    return this.spawns;
  }

  public WeatherService weather() {
    return this.weather;
  }

  public TimeService time() {
    return this.time;
  }

  public WorldCatalog catalog() {
    return this.catalog;
  }
}
