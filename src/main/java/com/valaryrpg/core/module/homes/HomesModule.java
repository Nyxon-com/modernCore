package com.valaryrpg.core.module.homes;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import com.valaryrpg.core.util.Teleports;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HomesModule implements Module, Listener {

    private final ValaryCorePlugin plugin;
    private HomeService homes;
    private Teleports teleports;

    public HomesModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "homes";
    }

    @Override
    public void enable() {
        this.teleports = new Teleports(this.plugin);
        this.homes = new HomeService(this.plugin, this.teleports);
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
        if (this.teleports != null) {
            this.teleports.cancel(event.getPlayer().getUniqueId());
        }
    }
}
