//made with love by nyxon

package com.modern.core.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Placeholders {

  private Placeholders() {
  }

  public static String apply(Player player, String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String filled = text;
    if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      filled = PlaceholderAPI.setPlaceholders(player, filled);
    }
    return filled.replaceAll("%[A-Za-z0-9_+-]+%", "");
  }
}
