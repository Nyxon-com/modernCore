//made with love by nyxon

package com.modern.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class Plugins {

  private Plugins() {
  }

  public static ModernCorePlugin core() {
    Plugin plugin = Bukkit.getPluginManager().getPlugin("modernCore");
    if (plugin instanceof ModernCorePlugin core && core.isEnabled()) {
      return core;
    }
    return null;
  }
}
