package com.valaryrpg.core.module.holograms;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.HandlerList;

/**
 * Hologram configurabili da plugins/ValaryRPG-Core/holo/.
 */
public final class HologramsModule implements Module {

    private final ValaryCorePlugin plugin;
    private HologramService holograms;
    private HologramListener listener;

    public HologramsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "holograms";
    }

    @Override
    public void enable() {
        this.holograms = new HologramService(this.plugin);
        this.listener = new HologramListener(this.holograms);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.holograms.start();
    }

    @Override
    public void disable() {
        if (this.holograms != null) {
            this.holograms.stop();
        }
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public HologramService holograms() {
        return this.holograms;
    }
}
