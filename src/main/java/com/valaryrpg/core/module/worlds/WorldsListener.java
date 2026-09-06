package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class WorldsListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final SpawnService spawns;
    private final WeatherService weather;

    public WorldsListener(ValaryCorePlugin plugin, SpawnService spawns, WeatherService weather) {
        this.plugin = plugin;
        this.spawns = spawns;
        this.weather = weather;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!this.weather.isLocked(event.getWorld())) {
            return;
        }
        event.setCancelled(true);
        this.weather.apply(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!this.weather.isLocked(event.getWorld())) {
            return;
        }
        event.setCancelled(true);
        this.weather.apply(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        this.weather.apply(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!this.plugin.configs().worlds().getBoolean("settings.spawn-on-join", false)) {
            return;
        }
        Player player = event.getPlayer();
        World world = this.spawns.resolveWorld(player);
        if (world != null) {
            player.teleportAsync(this.spawns.locationOf(world));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!this.plugin.configs().worlds().getBoolean("settings.spawn-on-respawn", true)) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        World world = this.spawns.resolveWorld(event.getPlayer());
        if (world != null) {
            event.setRespawnLocation(this.spawns.locationOf(world));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.spawns.cancelWarmup(event.getPlayer().getUniqueId());
    }
}
