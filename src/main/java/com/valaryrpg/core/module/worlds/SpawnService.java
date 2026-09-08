package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Spawn globale da /setspawn: /spawn ci porta da qualsiasi mondo.
 */
public final class SpawnService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Location> warmupOrigin = new HashMap<>();

    public SpawnService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void setSpawn(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        FileConfiguration yaml = this.plugin.configs().worlds();
        writeLocation(yaml, "spawn", world.getName(), location);
        writeLocation(yaml, "spawns." + world.getName(), null, location);
        yaml.set("settings.default-world", world.getName());
        this.plugin.configs().saveWorlds();
        world.setSpawnLocation(location);

        this.plugin.messages().send(player, "spawn.set", Map.of(
                "world", world.getName(),
                "x", format(location.getX()),
                "y", format(location.getY()),
                "z", format(location.getZ())
        ));
        Sounds.play(this.plugin, player, "spawn-set");
    }

    public void teleport(CommandSender initiator, Player target) {
        Location destination = globalLocation();
        if (destination == null || destination.getWorld() == null) {
            this.plugin.messages().send(initiator, "general.invalid-world", Map.of(
                    "world", this.plugin.configs().worlds().getString("settings.default-world", "world")
            ));
            return;
        }

        World world = destination.getWorld();
        boolean staffTeleport = initiator != target;
        int warmup = staffTeleport ? 0 : this.plugin.configs().worlds().getInt("settings.warmup-seconds", 0);

        if (warmup <= 0) {
            completeTeleport(initiator, target, world, destination);
            return;
        }

        this.plugin.messages().send(target, "spawn.warmup", Map.of("seconds", String.valueOf(warmup)));
        this.warmupOrigin.put(target.getUniqueId(), target.getLocation().clone());
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Location origin = this.warmupOrigin.remove(target.getUniqueId());
            if (origin == null || !target.isOnline()) {
                return;
            }
            boolean cancelOnMove = this.plugin.configs().worlds().getBoolean("settings.cancel-on-move", false);
            if (cancelOnMove && moved(origin, target.getLocation())) {
                this.plugin.messages().send(target, "spawn.cancelled-move");
                return;
            }
            completeTeleport(initiator, target, world, destination);
        }, warmup * 20L);
    }

    public Location locationOf(World world) {
        ConfigurationSection section = this.plugin.configs().worlds().getConfigurationSection("spawns." + world.getName());
        if (section == null) {
            return world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }
        return locationFrom(section, world);
    }

    /**
     * Join/respawn: spawn del mondo attuale solo se use-current-world-spawn è true.
     * Altrimenti lo spawn globale di /setspawn.
     */
    public Location spawnLocation(Player player) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        if (yaml.getBoolean("settings.use-current-world-spawn", false) && player.getWorld() != null) {
            return locationOf(player.getWorld());
        }
        return globalLocation();
    }

    public World resolveWorld(Player player) {
        Location location = spawnLocation(player);
        return location == null ? null : location.getWorld();
    }

    public void cancelWarmup(UUID uuid) {
        this.warmupOrigin.remove(uuid);
    }

    private Location globalLocation() {
        FileConfiguration yaml = this.plugin.configs().worlds();
        ConfigurationSection section = yaml.getConfigurationSection("spawn");
        if (section != null) {
            String worldName = section.getString("world");
            if (worldName != null && !worldName.isBlank()) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return locationFrom(section, world);
                }
            }
        }
        String defaultWorld = yaml.getString("settings.default-world", "world");
        World world = Bukkit.getWorld(defaultWorld);
        if (world == null) {
            return null;
        }
        return locationOf(world);
    }

    private void completeTeleport(CommandSender initiator, Player target, World world, Location destination) {
        this.plugin.lastLocations().remember(target);
        boolean custom = this.plugin.configs().worlds().contains("spawn.world");
        if (!custom) {
            this.plugin.messages().send(initiator, "spawn.not-set", Map.of("world", world.getName()));
        }
        target.teleportAsync(destination).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success) || !target.isOnline()) {
                return;
            }
            this.plugin.messages().send(target, "spawn.teleported", Map.of("world", world.getName()));
            if (this.plugin.configs().worlds().getBoolean("settings.teleport-sound", true)) {
                Sounds.play(this.plugin, target, "teleport-success");
            }
            if (initiator != target) {
                this.plugin.messages().send(initiator, "spawn.teleported-other", Map.of(
                        "target", target.getName(),
                        "world", world.getName()
                ));
                this.plugin.messages().send(target, "spawn.teleported-by-staff", Map.of(
                        "player", initiator.getName()
                ));
            }
        });
    }

    private static void writeLocation(FileConfiguration yaml, String path, String worldName, Location location) {
        if (worldName != null) {
            yaml.set(path + ".world", worldName);
        }
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private static Location locationFrom(ConfigurationSection section, World world) {
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private static boolean moved(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return true;
        }
        return from.toVector().distanceSquared(to.toVector()) > 0.04D;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
