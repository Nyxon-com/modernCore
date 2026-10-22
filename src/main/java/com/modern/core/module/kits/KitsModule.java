//made with love by nyxon

package com.modern.core.module.kits;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.HandlerList;

public final class KitsModule implements Module {

  private final ModernCorePlugin plugin;
  private KitService kits;
  private KitsListener listener;

  public KitsModule(ModernCorePlugin plugin) {
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
    if (this.kits != null) {
      this.kits.shutdown();
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
