package com.modern.core.module.warps;

import com.modern.core.ModernCorePlugin;
import com.modern.core.storage.StoredLocation;
import com.modern.core.util.Sounds;
import com.modern.core.util.Teleports;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class WarpService {

  private final ModernCorePlugin plugin;
  private final Teleports teleports;
  private final Pattern namePattern = Pattern.compile("[a-z0-9_]{1,32}");

  public WarpService(ModernCorePlugin plugin, Teleports teleports) {
    this.plugin = plugin;
    this.teleports = teleports;
  }

  public void setWarp(Player player, String rawName) {
    String name = normalize(rawName);
    if (!validName(name)) {
      this.plugin.messages().send(player, "warps.invalid-name");
      return;
    }
    Location location = player.getLocation();
    FileConfiguration yaml = this.plugin.configs().warps();
    String path = "warps." + name;
    yaml.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
    yaml.set(path + ".x", location.getX());
    yaml.set(path + ".y", location.getY());
    yaml.set(path + ".z", location.getZ());
    yaml.set(path + ".yaw", location.getYaw());
    yaml.set(path + ".pitch", location.getPitch());
    this.plugin.configs().saveWarps();
    this.plugin.messages().send(player, "warps.set", Map.of(
        "warp", name,
        "world", location.getWorld() == null ? "?" : location.getWorld().getName()
    ));
    Sounds.play(this.plugin, player, "spawn-set");
  }

  public void teleport(CommandSender initiator, Player target, String rawName) {
    String name = normalize(rawName);
    if (initiator == target && !canUse(initiator, name)) {
      this.plugin.messages().send(initiator, "warps.no-access", Map.of("warp", name));
      return;
    }
    StoredLocation warp = find(name);
    if (warp == null) {
      this.plugin.messages().send(initiator, "warps.not-found", Map.of("warp", name));
      return;
    }
    Location destination = warp.toLocation();
    if (destination == null) {
      this.plugin.messages().send(initiator, "general.invalid-world", Map.of("world", warp.worldName()));
      return;
    }

    boolean staff = initiator != target;
    int warmup = staff ? 0 : this.plugin.configs().warps().getInt("settings.warmup-seconds", 3);
    int cooldown = staff ? 0 : this.plugin.configs().warps().getInt("settings.cooldown-seconds", 5);
    boolean cancel = this.plugin.configs().warps().getBoolean("settings.cancel-on-move", true);
    this.teleports.go(target, destination, warmup, cooldown, cancel, player -> {
      this.plugin.messages().send(player, "warps.teleported", Map.of("warp", name));
      if (staff && initiator instanceof Player) {
        this.plugin.messages().send(initiator, "warps.teleported-other", Map.of(
            "target", player.getName(),
            "warp", name
        ));
        this.plugin.messages().send(player, "warps.teleported-by-staff", Map.of(
            "player", initiator.getName(),
            "warp", name
        ));
      }
    });
  }

  public void delete(CommandSender sender, String rawName) {
    String name = normalize(rawName);
    if (find(name) == null) {
      this.plugin.messages().send(sender, "warps.not-found", Map.of("warp", name));
      return;
    }
    this.plugin.configs().warps().set("warps." + name, null);
    this.plugin.configs().saveWarps();
    this.plugin.messages().send(sender, "warps.deleted", Map.of("warp", name));
  }

  public void list(CommandSender sender) {
    List<String> names = names(sender);
    if (names.isEmpty()) {
      this.plugin.messages().send(sender, "warps.list-empty");
      return;
    }
    this.plugin.messages().send(sender, "warps.list", Map.of(
        "count", String.valueOf(names.size()),
        "warps", String.join(", ", names)
    ));
  }

  public List<String> names() {
    ConfigurationSection section = this.plugin.configs().warps().getConfigurationSection("warps");
    if (section == null) {
      return List.of();
    }
    return new ArrayList<>(section.getKeys(false)).stream().sorted().collect(Collectors.toList());
  }

  public List<String> names(CommandSender sender) {
    return names().stream().filter(name -> canUse(sender, name)).collect(Collectors.toList());
  }

  public boolean canUse(CommandSender sender, String rawName) {
    if (!(sender instanceof Player)) {
      return true;
    }
    String perm = this.plugin.configs().warps().getString("warps." + normalize(rawName) + ".permission", "");
    return perm == null || perm.isBlank() || sender.hasPermission(perm);
  }

  private StoredLocation find(String name) {
    ConfigurationSection section = this.plugin.configs().warps().getConfigurationSection("warps." + name);
    if (section == null) {
      return null;
    }
    return new StoredLocation(
        new UUID(0, 0),
        name,
        section.getString("world", ""),
        section.getDouble("x"),
        section.getDouble("y"),
        section.getDouble("z"),
        (float) section.getDouble("yaw"),
        (float) section.getDouble("pitch")
    );
  }

  private boolean validName(String name) {
    int max = this.plugin.configs().warps().getInt("settings.max-name-length", 16);
    return !name.isBlank() && name.length() <= max && this.namePattern.matcher(name).matches();
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }
}
