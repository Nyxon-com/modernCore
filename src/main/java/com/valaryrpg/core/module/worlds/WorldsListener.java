package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class WorldsListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final WorldCatalog catalog;
    private final SpawnService spawns;
    private final WeatherService weather;
    private final TimeService time;

    public WorldsListener(ValaryCorePlugin plugin, WorldCatalog catalog, SpawnService spawns, WeatherService weather, TimeService time) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.spawns = spawns;
        this.weather = weather;
        this.time = time;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!this.weather.allowsWeatherChange(event.getWorld(), event.toWeatherState())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!this.weather.allowsThunderChange(event.getWorld(), event.toThunderState())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (this.plugin.configs().worlds().getBoolean("settings.import-on-start", true) && this.catalog.importWorld(world)) {
            this.plugin.configs().saveWorlds();
        }
        this.catalog.apply(world);
        this.weather.applyCatalog(world);
        this.weather.apply(world);
        this.time.apply(world);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyWorldFlags(event.getPlayer(), event.getPlayer().getWorld());
        if (!this.plugin.configs().worlds().getBoolean("settings.spawn-on-join", false)) {
            return;
        }
        Player player = event.getPlayer();
        Location destination = this.spawns.spawnLocation(player);
        if (destination != null) {
            player.teleportAsync(destination);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyWorldFlags(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getWorld() != null && !this.catalog.hunger(player.getWorld())) {
            event.setCancelled(true);
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
        Location destination = this.spawns.spawnLocation(event.getPlayer());
        if (destination != null) {
            event.setRespawnLocation(destination);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.spawns.cancelWarmup(event.getPlayer().getUniqueId());
    }

    private void applyWorldFlags(Player player, World world) {
        if (world == null) {
            return;
        }
        if (this.catalog.forceGamemode(world)) {
            player.setGameMode(this.catalog.gamemode(world));
        }
        if (this.catalog.allowFlight(world)) {
            player.setAllowFlight(true);
        }
    }
}
