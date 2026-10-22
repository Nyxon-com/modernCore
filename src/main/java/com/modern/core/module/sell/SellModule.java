//made with love by nyxon

package com.modern.core.module.sell;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.HandlerList;

public final class SellModule implements Module {

  private final ModernCorePlugin plugin;
  private SellService sell;
  private SellListener listener;

  public SellModule(ModernCorePlugin plugin) {
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
