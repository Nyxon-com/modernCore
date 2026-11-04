package com.modern.core.module.features;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.Module;

public final class FeaturesModule implements Module {

  private final ModernCorePlugin plugin;
  private SkinService skins;

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
  }

  @Override
  public void disable() {
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
