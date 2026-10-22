//made with love by nyxon

package com.modern.core.gui;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.MmoItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class PanelItems {

  private PanelItems() {
  }

  public static ItemStack from(ModernCorePlugin plugin, ConfigurationSection section, Player viewer) {
    if (section == null) {
      return new ItemStack(Material.AIR);
    }
    int amount = Math.max(1, Math.min(64, section.getInt("amount", 1)));
    ItemStack item = MmoItems.stack(
        section.getString("mmoitem-type", ""),
        section.getString("mmoitem-id", ""),
        amount
    );
    if (item == null) {
      item = named(section.getString("material", "STONE"), Material.STONE);
      item.setAmount(amount);
    }
    String playerName = viewer == null ? "" : viewer.getName();
    ItemStack icon = item;
    icon.editMeta(meta -> {
      String name = section.getString("name", section.getString("display-name", ""));
      if (name != null && !name.isBlank()) {
        meta.displayName(plugin.messages().deserialize(placeholders(name, playerName)));
      }
      List<String> lore = section.getStringList("lore");
      if (!lore.isEmpty()) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
          lines.add(plugin.messages().deserialize(placeholders(line, playerName)));
        }
        meta.lore(lines);
      }
      if (section.getInt("custom-model-data", 0) > 0) {
        meta.setCustomModelData(section.getInt("custom-model-data"));
      }
      if (section.getBoolean("glow", false) || section.getBoolean("enchanted", false)) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }
      if (section.getBoolean("hide-flags", false)) {
        meta.addItemFlags(ItemFlag.values());
      }
      if (meta instanceof SkullMeta skull) {
        String owner = section.getString("skull-owner", "");
        if (owner != null && !owner.isBlank()) {
          skull.setOwningPlayer(plugin.getServer().getOfflinePlayer(owner));
        }
      }
    });
    return icon;
  }

  public static ItemStack filler(ModernCorePlugin plugin, ConfigurationSection gui) {
    ConfigurationSection fill = gui == null ? null : gui.getConfigurationSection("fill");
    if (fill == null) {
      ItemStack item = named(gui == null ? "GRAY_STAINED_GLASS_PANE" : gui.getString("fill-material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
      item.editMeta(meta -> meta.displayName(Component.empty()));
      return item;
    }
    if (!fill.getBoolean("enabled", true)) {
      return new ItemStack(Material.AIR);
    }
    return from(plugin, fill, null);
  }

  public static ItemStack named(String materialName, Material fallback) {
    Material material = Material.matchMaterial(materialName == null ? "" : materialName);
    return new ItemStack(material == null ? fallback : material);
  }

  private static String placeholders(String text, String player) {
    return text.replace("{player}", player).replace("{player_name}", player);
  }
}
