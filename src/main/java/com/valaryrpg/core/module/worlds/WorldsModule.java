package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * Modulo mondi: spawn per mondo e lock meteo. Hook Multiverse puramente opzionale.
 */
public final class WorldsModule implements Module {

    private final ValaryCorePlugin plugin;
    private SpawnService spawns;
    private WeatherService weather;
    private WorldsListener listener;

    public WorldsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "worlds";
    }

    @Override
    public void enable() {
        this.spawns = new SpawnService(this.plugin);
        this.weather = new WeatherService(this.plugin);
        this.listener = new WorldsListener(this.plugin, this.spawns, this.weather);
        Bukkit.getPluginManager().registerEvents(this.listener, this.plugin);
        this.weather.applyAll();

        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            this.plugin.getLogger().info("Multiverse-Core rilevato: i mondi caricati sono gestiti via Bukkit API.");
        }
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public SpawnService spawns() {
        return this.spawns;
    }

    public WeatherService weather() {
        return this.weather;
    }
}
