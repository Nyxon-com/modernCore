package com.modern.core.gui;

import org.bukkit.configuration.ConfigurationSection;

public final class GuiLayouts {

  private GuiLayouts() {
  }

  public static ConfigurationSection button(ConfigurationSection gui, int slot) {
    if (gui == null) {
      return null;
    }
    ConfigurationSection slots = gui.getConfigurationSection("slots");
    if (slots == null) {
      return null;
    }
    for (String key : slots.getKeys(false)) {
      if (SlotSpec.contains(key, slot)) {
        return slots.getConfigurationSection(key);
      }
    }
    return null;
  }

  public static String action(ConfigurationSection button) {
    if (button == null) {
      return "none";
    }
    String action = button.getString("action", "none");
    return action == null || action.isBlank() ? "none" : action.toLowerCase(java.util.Locale.ROOT);
  }

  public static int rows(ConfigurationSection gui, int fallback) {
    if (gui == null) {
      return fallback;
    }
    return Math.min(6, Math.max(1, gui.getInt("rows", fallback)));
  }
}
