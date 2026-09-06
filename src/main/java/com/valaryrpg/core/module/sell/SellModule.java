package com.valaryrpg.core.module.sell;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.event.HandlerList;

/**
 * Vendita item tramite GUI e prices.yml.
 */
public final class SellModule implements Module {

    private final ValaryCorePlugin plugin;
    private SellService sell;
    private SellListener listener;

    public SellModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "sell";
    }

    @Override
    public void enable() {
        this.sell = new SellService(this.plugin);
        this.listener = new SellListener(this.sell);
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

    public SellService sell() {
        return this.sell;
    }
}
