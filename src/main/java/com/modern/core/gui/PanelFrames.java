package com.modern.core.gui;

import com.modern.core.ModernCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class PanelFrames {

  private PanelFrames() {
  }

  public static void apply(ModernCorePlugin plugin, Inventory inventory, ConfigurationSection gui) {
    ConfigurationSection frame = gui == null ? null : gui.getConfigurationSection("frame");
    if (frame == null || !frame.getBoolean("enabled", false) || inventory == null) {
      return;
    }
    int size = inventory.getSize();
    int rows = size / 9;
    if (rows < 1) {
      return;
    }
    ItemStack edges = PanelItems.from(plugin, frame.getConfigurationSection("edges"), null);
    ItemStack accents = PanelItems.from(plugin, frame.getConfigurationSection("accents"), null);
    ItemStack corners = PanelItems.from(plugin, frame.getConfigurationSection("corners"), null);
    for (int slot = 0; slot < size; slot++) {
      if (!perimeter(slot, rows)) {
        continue;
      }
      ItemStack item = corner(slot, rows) ? corners : accent(slot, rows) ? accents : edges;
      if (item != null && !item.getType().isAir()) {
        inventory.setItem(slot, item.clone());
      }
    }
  }

  private static boolean perimeter(int slot, int rows) {
    int col = slot % 9;
    int row = slot / 9;
    return row == 0 || row == rows - 1 || col == 0 || col == 8;
  }

  private static boolean corner(int slot, int rows) {
    int col = slot % 9;
    int row = slot / 9;
    return (row == 0 || row == rows - 1) && (col == 0 || col == 8);
  }

  private static boolean accent(int slot, int rows) {
    int col = slot % 9;
    int row = slot / 9;
    if (row == 0 && (col == 1 || col == 7)) {
      return true;
    }
    if (row == rows - 1 && (col == 1 || col == 7)) {
      return true;
    }
    if (col == 0 && (row == 1 || row == rows - 2) && rows > 2) {
      return true;
    }
    return col == 8 && (row == 1 || row == rows - 2) && rows > 2;
  }
}
