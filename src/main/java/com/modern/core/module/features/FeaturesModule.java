//made with love by nyxon

package com.modern.core.module.features;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;
import org.bukkit.event.HandlerList;

public final class FeaturesModule implements Module {

  private final ModernCorePlugin plugin;
  private SkinService skins;
  private OverheadService overhead;

  public FeaturesModule(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String id() {
    return "features";
  }

  @Override
  public void enable() {
    this.skins = new SkinService(this.plugin);
    this.overhead = new OverheadService(this.plugin);
    this.plugin.getServer().getPluginManager().registerEvents(this.overhead, this.plugin);
    this.overhead.start();
  }

  @Override
  public void disable() {
    if (this.overhead != null) {
      HandlerList.unregisterAll(this.overhead);
      this.overhead.stop();
      this.overhead = null;
    }
    this.skins = null;
  }

  @Override
  public void reload() {
    disable();
    enable();
  }

  public SkinService skins() {
    return this.skins;
  }
}
