package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.worlds.WorldsModule;
import com.valaryrpg.core.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class LimitYService {

    private final ValaryCorePlugin plugin;
    private BukkitTask task;

    public LimitYService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!this.plugin.configs().mechanics().getBoolean("limity.enabled", true)) {
            return;
        }
        int interval = Math.max(1, this.plugin.configs().mechanics().getInt("limity.check-interval-ticks", 10));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public String describe(World world) {
        Bounds bounds = boundsOf(world);
        if (bounds == null) {
            return "";
        }
        return bounds.min + " → " + bounds.max;
    }

    public void set(World world, double min, double max) {
        String path = "limity.worlds." + world.getName();
        this.plugin.configs().mechanics().set(path + ".min", min);
        this.plugin.configs().mechanics().set(path + ".max", max);
        this.plugin.configs().saveMechanics();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Perms.has(this.plugin, player, "limity-bypass")) {
                continue;
            }
            World world = player.getWorld();
            Bounds bounds = boundsOf(world);
            if (bounds == null) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getY() >= bounds.min && location.getY() <= bounds.max) {
                continue;
            }
            Location destination;
            WorldsModule worlds = this.plugin.modules().get("worlds");
            if (worlds != null) {
                destination = worlds.spawns().locationOf(world);
            } else {
                destination = world.getSpawnLocation();
            }
            player.teleport(destination);
            this.plugin.messages().send(player, "limity.blocked", Map.of(
                    "min", String.valueOf(bounds.min),
                    "max", String.valueOf(bounds.max),
                    "world", world.getName()
            ));
        }
    }

    private Bounds boundsOf(World world) {
        ConfigurationSection section = this.plugin.configs().mechanics().getConfigurationSection("limity.worlds." + world.getName());
        if (section == null) {
            return null;
        }
        return new Bounds(section.getDouble("min"), section.getDouble("max"));
    }

    private record Bounds(double min, double max) {
    }
}
