package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.HandlerList;

/**
 * Kit GUI, cooldown SQL e kit first-join.
 */
public final class KitsModule implements Module {

    private final ValaryCorePlugin plugin;
    private KitService kits;
    private KitsListener listener;

    public KitsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "kits";
    }

    @Override
    public void enable() {
        this.kits = new KitService(this.plugin);
        this.listener = new KitsListener(this.kits);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
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

    public KitService kits() {
        return this.kits;
    }
}
