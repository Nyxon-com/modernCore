package com.modern.core.module.worlds;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public final class WeatherService {

  public enum WeatherLock {
    CLEAR,
    RAIN,
    THUNDER;

    public static WeatherLock from(String raw) {
      if (raw == null) {
        return CLEAR;
      }
      return switch (raw.trim().toUpperCase(Locale.ROOT)) {
        case "RAIN", "STORM", "DOWNFALL" -> RAIN;
        case "THUNDER", "THUNDERSTORM" -> THUNDER;
        default -> CLEAR;
      };
    }
  }

  private final ModernCorePlugin plugin;
  private BukkitTask task;
  private boolean applying;

  public WeatherService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    stop();
    applyAll();
    long interval = Math.max(1L, this.plugin.configs().worlds().getLong("settings.time-lock-interval-ticks", 20L));
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickPlayers, interval, interval);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
  }

  public void applyAll() {
    for (World world : Bukkit.getWorlds()) {
      apply(world);
    }
  }

  public void applyCatalogAll() {
    for (World world : Bukkit.getWorlds()) {
      applyCatalog(world);
    }
  }

  public void applyCatalog(World world) {
    if (isLocked(world)) {
      return;
    }
    String type = this.plugin.configs().worlds().getString("worlds." + world.getName() + ".weather", "UNSET");
    if (type == null || type.isBlank() || type.equalsIgnoreCase("UNSET")) {
      return;
    }
    applyType(world, WeatherLock.from(type));
  }

  public void apply(World world) {
    WeatherLock lock = lockOf(world.getName());
    if (lock == null) {
      return;
    }
    applyType(world, lock);
  }

  public void applyTo(Player player) {
    World world = player.getWorld();
    if (world == null) {
      return;
    }
    WeatherLock lock = lockOf(world.getName());
    if (lock == null) {
      player.resetPlayerWeather();
      return;
    }
    player.setPlayerWeather(lock == WeatherLock.CLEAR ? WeatherType.CLEAR : WeatherType.DOWNFALL);
  }

  public boolean isLocked(World world) {
    return lockOf(world.getName()) != null;
  }

  public boolean isApplying() {
    return this.applying;
  }

  public boolean allowsWeatherChange(World world, boolean raining) {
    if (this.applying) {
      return true;
    }
    WeatherLock lock = lockOf(world.getName());
    if (lock == null) {
      return true;
    }
    return switch (lock) {
      case CLEAR -> !raining;
      case RAIN, THUNDER -> raining;
    };
  }

  public boolean allowsThunderChange(World world, boolean thunder) {
    if (this.applying) {
      return true;
    }
    WeatherLock lock = lockOf(world.getName());
    if (lock == null) {
      return true;
    }
    return switch (lock) {
      case CLEAR, RAIN -> !thunder;
      case THUNDER -> thunder;
    };
  }

  public void lock(World world, WeatherLock type) {
    FileConfiguration yaml = this.plugin.configs().worlds();
    String path = "weather." + world.getName();
    yaml.set(path + ".lock", true);
    yaml.set(path + ".type", type.name());
    this.plugin.configs().saveWorlds();
    applyType(world, type);
  }

  public void unlock(World world) {
    FileConfiguration yaml = this.plugin.configs().worlds();
    yaml.set("weather." + world.getName(), null);
    this.plugin.configs().saveWorlds();
    world.setGameRule(GameRules.ADVANCE_WEATHER, true);
    for (Player player : world.getPlayers()) {
      player.resetPlayerWeather();
    }
  }

  private WeatherLock lockOf(String worldName) {
    ConfigurationSection section = this.plugin.configs().worlds().getConfigurationSection("weather." + worldName);
    if (section == null || !section.getBoolean("lock", false)) {
      return null;
    }
    return WeatherLock.from(section.getString("type", "CLEAR"));
  }

  private void applyType(World world, WeatherLock type) {
    this.applying = true;
    try {
      world.setGameRule(GameRules.ADVANCE_WEATHER, false);
      switch (type) {
        case CLEAR -> {
          world.setStorm(false);
          world.setThundering(false);
          world.setClearWeatherDuration(Integer.MAX_VALUE);
        }
        case RAIN -> {
          world.setStorm(true);
          world.setThundering(false);
          world.setWeatherDuration(Integer.MAX_VALUE);
        }
        case THUNDER -> {
          world.setStorm(true);
          world.setThundering(true);
          world.setWeatherDuration(Integer.MAX_VALUE);
          world.setThunderDuration(Integer.MAX_VALUE);
        }
      }
      WeatherType playerWeather = type == WeatherLock.CLEAR ? WeatherType.CLEAR : WeatherType.DOWNFALL;
      for (Player player : world.getPlayers()) {
        player.setPlayerWeather(playerWeather);
      }
    } finally {
      this.applying = false;
    }
  }

  private void tickPlayers() {
    boolean particles = this.plugin.configs().worlds().getBoolean("settings.weather-lock-client-particles", true);
    int count = Math.max(0, this.plugin.configs().worlds().getInt("settings.weather-lock-particle-count", 48));
    Particle particle = particle();
    for (World world : Bukkit.getWorlds()) {
      WeatherLock lock = lockOf(world.getName());
      if (lock == null) {
        continue;
      }
      WeatherType playerWeather = lock == WeatherLock.CLEAR ? WeatherType.CLEAR : WeatherType.DOWNFALL;
      for (Player player : world.getPlayers()) {
        player.setPlayerWeather(playerWeather);
        if (particles && particle != null && lock != WeatherLock.CLEAR && count > 0) {
          player.spawnParticle(
              particle,
              player.getLocation().add(0.0, 8.0, 0.0),
              count,
              6.0,
              4.0,
              6.0,
              0.3
          );
        }
      }
    }
  }

  private Particle particle() {
    String name = this.plugin.configs().worlds().getString("settings.weather-lock-particle", "FALLING_WATER");
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return Particle.FALLING_WATER;
    }
  }
}
