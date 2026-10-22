//made with love by nyxon

package com.modern.core.module.holograms;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.HandlerList;

public final class HologramsModule implements Module {

  private final ModernCorePlugin plugin;
  private HologramService holograms;
  private HologramListener listener;
  private boolean initialized;

  public HologramsModule(ModernCorePlugin plugin) {
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
    if (this.initialized) {
      this.holograms.start();
    } else {
      this.holograms.initialize();
      this.initialized = true;
    }
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
