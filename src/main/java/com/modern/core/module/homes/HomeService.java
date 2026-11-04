package com.modern.core.module.homes;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StoredLocation;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import com.modern.core.util.Teleports;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class HomeService {

  private final ModernCorePlugin plugin;
  private final HomeRepository repository;
  private final Teleports teleports;
  private final Pattern namePattern = Pattern.compile("[a-z0-9_]{1,32}");

  public HomeService(ModernCorePlugin plugin, Teleports teleports) {
    this.plugin = plugin;
    this.repository = new HomeRepository(plugin);
    this.teleports = teleports;
  }

  public void setHome(Player player, String rawName) {
    String name = normalize(rawName);
    if (!validName(name)) {
      this.plugin.messages().send(player, "homes.invalid-name");
      return;
    }

    int limit = limitOf(player);
    Location location = player.getLocation().clone();
    if (location.getWorld() != null) {
      for (String blocked : this.plugin.configs().homes().getStringList("settings.blocked-worlds")) {
        if (blocked.equalsIgnoreCase(location.getWorld().getName())) {
          this.plugin.messages().send(player, "homes.blocked-world", Map.of(
              "world", location.getWorld().getName()
          ));
          Sounds.play(this.plugin, player, "command-denied");
          return;
        }
      }
    }
    async(() -> {
      Optional<StoredLocation> existing = this.repository.find(player.getUniqueId(), name);
      int count = this.repository.count(player.getUniqueId());
      if (existing.isEmpty() && limit >= 0 && count >= limit) {
        sync(() -> {
          this.plugin.messages().send(player, "homes.limit-reached", Map.of(
              "limit", String.valueOf(limit)
          ));
          Sounds.play(this.plugin, player, "command-denied");
        });
        return;
      }
      this.repository.upsert(StoredLocation.from(player.getUniqueId(), name, location));
      sync(() -> {
        this.plugin.messages().send(player, "homes.set", Map.of(
            "home", name,
            "world", worldName(location),
            "x", format(location.getX()),
            "y", format(location.getY()),
            "z", format(location.getZ())
        ));
        Sounds.play(this.plugin, player, "spawn-set");
      });
    });
  }

  public void teleport(Player player, String rawName) {
    String name = normalize(rawName);
    async(() -> {
      Optional<StoredLocation> home = this.repository.find(player.getUniqueId(), name);
      sync(() -> {
        if (home.isEmpty()) {
          this.plugin.messages().send(player, "homes.not-found", Map.of("home", name));
          return;
        }
        Location destination = home.get().toLocation();
        if (destination == null) {
          this.plugin.messages().send(player, "general.invalid-world", Map.of("world", home.get().worldName()));
          return;
        }
        int warmup = this.plugin.configs().homes().getInt("settings.warmup-seconds", 3);
        int cooldown = this.plugin.configs().homes().getInt("settings.cooldown-seconds", 5);
        boolean cancel = this.plugin.configs().homes().getBoolean("settings.cancel-on-move", true);
        this.teleports.go(player, destination, warmup, cooldown, cancel, target ->
            this.plugin.messages().send(target, "homes.teleported", Map.of("home", name)));
      });
    });
  }

  public void delete(Player player, String rawName) {
    String name = normalize(rawName);
    async(() -> {
      boolean deleted = this.repository.delete(player.getUniqueId(), name);
      sync(() -> {
        if (deleted) {
          this.plugin.messages().send(player, "homes.deleted", Map.of("home", name));
        } else {
          this.plugin.messages().send(player, "homes.not-found", Map.of("home", name));
        }
      });
    });
  }

  public void list(CommandSender sender, UUID uuid) {
    async(() -> {
      List<StoredLocation> homes = this.repository.list(uuid);
      sync(() -> {
        if (homes.isEmpty()) {
          this.plugin.messages().send(sender, "homes.list-empty");
          return;
        }
        String names = homes.stream().map(StoredLocation::name).collect(Collectors.joining(", "));
        this.plugin.messages().send(sender, "homes.list", Map.of(
            "count", String.valueOf(homes.size()),
            "homes", names
        ));
      });
    });
  }

  public List<String> names(UUID uuid) {
    return this.repository.list(uuid).stream().map(StoredLocation::name).toList();
  }

  public String defaultName() {
    return normalize(this.plugin.configs().homes().getString("settings.default-name", "home"));
  }

  public int limitOf(Player player) {
    if (Perms.has(this.plugin, player, "home-unlimited")) {
      return -1;
    }
    int highest = this.plugin.configs().homes().getInt("limits.default", 1);
    ConfigurationSection groups = this.plugin.configs().homes().getConfigurationSection("limits.groups");
    if (groups == null) {
      return highest;
    }
    String prefix = this.plugin.configs().homes().getString("limits.permission-prefix", "valary.home.limit.");
    for (String group : groups.getKeys(false)) {
      if (!player.hasPermission(prefix + group)) {
        continue;
      }
      int value = groups.getInt(group);
      if (value < 0) {
        return -1;
      }
      highest = Math.max(highest, value);
    }
    return highest;
  }

  private boolean validName(String name) {
    int max = this.plugin.configs().homes().getInt("settings.max-name-length", 16);
    return name.length() <= max && this.namePattern.matcher(name).matches();
  }

  private String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return defaultName();
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private void async(Runnable task) {
    Tasks.async(this.plugin, task);
  }

  private void sync(Runnable task) {
    Tasks.sync(this.plugin, task);
  }

  private static String worldName(Location location) {
    return location.getWorld() == null ? "?" : location.getWorld().getName();
  }

  private static String format(double value) {
    return String.format(Locale.US, "%.2f", value);
  }
}
