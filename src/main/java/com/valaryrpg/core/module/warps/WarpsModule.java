package com.valaryrpg.core.module.warps;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class WarpsModule implements Module, Listener {

    private final ValaryCorePlugin plugin;
    private WarpService warps;

    public WarpsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "warps";
    }

    @Override
    public void enable() {
        this.warps = new WarpService(this.plugin, this.plugin.teleports());
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

    public WarpService warps() {
        return this.warps;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.teleports().cancel(event.getPlayer().getUniqueId());
    }
}
