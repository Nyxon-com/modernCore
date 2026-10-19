package com.modern.core.module.homes;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HomesModule implements Module, Listener {

    private final ModernCorePlugin plugin;
    private HomeService homes;

    public HomesModule(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "homes";
    }

    @Override
    public void enable() {
        this.homes = new HomeService(this.plugin, this.plugin.teleports());
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public HomeService homes() {
        return this.homes;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.teleports().cancel(event.getPlayer().getUniqueId());
    }
}
