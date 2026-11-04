package com.modern.core.gui;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.ActionCommands;
import com.modern.core.util.Sounds;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class PanelClicks {

  private PanelClicks() {
  }

  public static boolean run(ModernCorePlugin plugin, Player player, ConfigurationSection button) {
    if (button == null) {
      return false;
    }
    Sounds.playRaw(plugin, player, button.getString("click-sound", ""), "gui.click-sound");
    return ActionCommands.run(plugin, player, button.getStringList("commands"));
  }

  public static void openSound(ModernCorePlugin plugin, Player player, ConfigurationSection gui) {
    if (gui != null) {
      Sounds.playRaw(plugin, player, gui.getString("open-sound", ""), "gui.open-sound");
    }
  }
}
