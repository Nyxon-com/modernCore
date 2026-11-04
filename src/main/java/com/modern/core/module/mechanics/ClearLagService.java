package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClearLagService {

  private final ModernCorePlugin plugin;
  private final GroundItemService groundItems;
  private BukkitTask task;
  private int secondsLeft;

  public ClearLagService(ModernCorePlugin plugin, GroundItemService groundItems) {
    this.plugin = plugin;
    this.groundItems = groundItems;
  }

  public void start() {
    stop();
    if (!enabled()) {
      return;
    }
    this.secondsLeft = intervalSeconds();
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
  }

  public boolean enabled() {
    return yaml().getBoolean("clearlag.enabled", true);
  }

  public int clearNow() {
    int removed = removeGroundEntities();
    broadcast("clearlag.cleared", Map.of("count", String.valueOf(removed)));
    this.secondsLeft = intervalSeconds();
    return removed;
  }

  public int secondsLeft() {
    return Math.max(0, this.secondsLeft);
  }

  private void tick() {
    if (!enabled()) {
      return;
    }
    this.secondsLeft--;
    if (this.secondsLeft < 0) {
      this.secondsLeft = 0;
    }
    Set<Integer> warnings = warningSeconds();
    if (this.secondsLeft > 0 && warnings.contains(this.secondsLeft)) {
      broadcast("clearlag.warning", Map.of("seconds", String.valueOf(this.secondsLeft)));
    }
    if (this.secondsLeft <= 0) {
      clearNow();
    }
  }

  private int removeGroundEntities() {
    int removed = 0;
    List<String> only = yaml().getStringList("clearlag.worlds");
    List<String> exclude = yaml().getStringList("clearlag.exclude-worlds");
    boolean arrows = yaml().getBoolean("clearlag.remove-arrows", true);
    boolean xp = yaml().getBoolean("clearlag.remove-xp-orbs", false);
    for (World world : Bukkit.getWorlds()) {
      if (!worldAllowed(world.getName(), only, exclude)) {
        continue;
      }
      for (Entity entity : List.copyOf(world.getEntities())) {
        if (entity instanceof Player) {
          continue;
        }
        if (entity instanceof Item item) {
          this.groundItems.remove(item.getUniqueId());
          item.remove();
          removed++;
          continue;
        }
        if (arrows && entity instanceof AbstractArrow) {
          entity.remove();
          removed++;
          continue;
        }
        if (xp && entity instanceof ExperienceOrb) {
          entity.remove();
          removed++;
        }
      }
    }
    return removed;
  }

  private static boolean worldAllowed(String world, List<String> only, List<String> exclude) {
    for (String entry : exclude) {
      if (entry != null && entry.equalsIgnoreCase(world)) {
        return false;
      }
    }
    if (only == null || only.isEmpty()) {
      return true;
    }
    for (String entry : only) {
      if (entry != null && entry.equalsIgnoreCase(world)) {
        return true;
      }
    }
    return false;
  }

  private void broadcast(String key, Map<String, String> placeholders) {
    Bukkit.getServer().sendMessage(this.plugin.messages().component(key, placeholders));
  }

  private int intervalSeconds() {
    return Math.max(30, yaml().getInt("clearlag.interval-seconds", 300));
  }

  private Set<Integer> warningSeconds() {
    Set<Integer> values = new HashSet<>();
    for (int value : yaml().getIntegerList("clearlag.warnings")) {
      if (value > 0) {
        values.add(value);
      }
    }
    if (values.isEmpty()) {
      values.add(30);
      values.add(10);
      values.add(5);
    }
    return values;
  }

  private FileConfiguration yaml() {
    return this.plugin.configs().mechanics();
  }
}
