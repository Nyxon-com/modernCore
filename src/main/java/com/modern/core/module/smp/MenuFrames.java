package com.modern.core.module.smp;

import net.kyori.adventure.text.Component;


import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MenuFrames {
  private final SmpContext plugin;

  public MenuFrames(SmpContext plugin) {
    this.plugin = plugin;
  }

  public int rows(String path, int fallback) {
    int rows = this.plugin.getConfig().getInt(path, fallback);
    if (rows < 1) {
      return 1;
    }
    return Math.min(rows, 6);
  }

  public Component title(String path, String fallback, Map<String, String> values) {
    return SmpText.parse(apply(this.plugin.getConfig().getString(path, fallback), values));
  }

  public List<Integer> slots(String path, String fallback) {
    List<Integer> parsed = new ArrayList<>();
    if (this.plugin.getConfig().isList(path)) {
      for (int slot : this.plugin.getConfig().getIntegerList(path)) {
        add(parsed, slot);
      }
      if (!parsed.isEmpty()) {
        return parsed;
      }
    }
    parseSpec(this.plugin.getConfig().getString(path, fallback), parsed);
    return parsed;
  }

  public List<String> lines(String path, List<String> fallback, Map<String, String> values) {
    List<String> raw = this.plugin.getConfig().getStringList(path);
    if (raw.isEmpty()) {
      raw = fallback;
    }
    List<String> out = new ArrayList<>();
    for (String line : raw) {
      out.add(apply(line, values));
    }
    return out;
  }

  public String text(String path, String fallback, Map<String, String> values) {
    return apply(this.plugin.getConfig().getString(path, fallback), values);
  }

  public ItemStack stack(Material material, String name, List<String> lore) {
    ItemStack stack = new ItemStack(material == null ? Material.CHEST : material);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(SmpText.parse(name));
    if (!lore.isEmpty()) {
      List<Component> lines = new ArrayList<>();
      for (String line : lore) {
        lines.add(SmpText.parse(line));
      }
      meta.lore(lines);
    }
    stack.setItemMeta(meta);
    return stack;
  }

  public void fill(Inventory inventory, String path) {
    Material material = Material.matchMaterial(this.plugin.getConfig().getString(path + ".material", ""));
    if (material == null || material.isAir()) {
      return;
    }
    ItemStack pane = stack(material, this.plugin.getConfig().getString(path + ".name", " "),
        this.plugin.getConfig().getStringList(path + ".lore"));
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      if (inventory.getItem(slot) == null) {
        inventory.setItem(slot, pane.clone());
      }
    }
  }

  public static String apply(String raw, Map<String, String> values) {
    String text = raw == null ? "" : raw;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      text = text.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return text;
  }

  private static void parseSpec(String spec, List<Integer> parsed) {
    if (spec == null || spec.isBlank()) {
      return;
    }
    for (String part : spec.split(",")) {
      String token = part.trim();
      int dash = token.indexOf('-');
      if (dash > 0) {
        try {
          int from = Integer.parseInt(token.substring(0, dash).trim());
          int to = Integer.parseInt(token.substring(dash + 1).trim());
          int step = from <= to ? 1 : -1;
          for (int slot = from; step > 0 ? slot <= to : slot >= to; slot += step) {
            add(parsed, slot);
          }
        } catch (NumberFormatException ignored) {
          continue;
        }
      } else {
        try {
          add(parsed, Integer.parseInt(token));
        } catch (NumberFormatException ignored) {
          continue;
        }
      }
    }
  }

  private static void add(List<Integer> parsed, int slot) {
    if (slot >= 0 && slot < 54 && !parsed.contains(slot)) {
      parsed.add(slot);
    }
  }
}
