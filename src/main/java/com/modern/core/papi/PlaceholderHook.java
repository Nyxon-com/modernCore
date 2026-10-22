//made with love by nyxon

package com.modern.core.papi;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;

public final class PlaceholderHook {

  private PlaceholderHook() {
  }

  public static void register(ModernCorePlugin plugin) {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
      return;
    }
    try {
      Class<?> expansion = Class.forName("com.modern.core.papi.ModernExpansion");
      Object instance = expansion.getConstructor(ModernCorePlugin.class).newInstance(plugin);
      Object registered = expansion.getMethod("register").invoke(instance);
      if (registered instanceof Boolean ok && ok) {
        plugin.getLogger().info("PlaceholderAPI: expansion valary registrata.");
      }
    } catch (ReflectiveOperationException exception) {
      plugin.getLogger().warning("PlaceholderAPI hook fallito: " + exception.getMessage());
    }
  }
}
