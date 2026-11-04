package com.modern.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MmoItems {

  private MmoItems() {
  }

  public static boolean matches(ItemStack item, String type, String id) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    if ((type == null || type.isBlank()) && (id == null || id.isBlank())) {
      return true;
    }
    String[] found = nbt(item);
    if (found == null) {
      return false;
    }
    boolean typeOk = type == null || type.isBlank() || type.equalsIgnoreCase(found[0]);
    boolean idOk = id == null || id.isBlank() || id.equalsIgnoreCase(found[1]);
    return typeOk && idOk;
  }

  public static ItemStack stack(String type, String id, int amount) {
    if (type == null || type.isBlank() || id == null || id.isBlank()) {
      return null;
    }
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return null;
    }
    try {
      Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
      Object plugin = mmoClass.getField("plugin").get(null);
      Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
      Object mmoType = typeClass.getMethod("get", String.class).invoke(null, type);
      if (mmoType == null) {
        return null;
      }
      Object built = mmoClass.getMethod("getItem", typeClass, String.class).invoke(plugin, mmoType, id);
      if (!(built instanceof ItemStack stack) || stack.getType().isAir()) {
        return null;
      }
      ItemStack clone = stack.clone();
      clone.setAmount(Math.max(1, Math.min(64, amount)));
      return clone;
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  public static void give(Player player, String type, String id, int amount, String commandTemplate) {
    ItemStack stack = stack(type, id, amount);
    if (stack != null) {
      player.getInventory().addItem(stack);
      return;
    }
    String template = commandTemplate == null || commandTemplate.isBlank()
        ? "mi give {player} {type} {id} {amount}"
        : commandTemplate;
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), template
        .replace("{player}", player.getName())
        .replace("{type}", type)
        .replace("{id}", id)
        .replace("{amount}", String.valueOf(Math.max(1, amount))));
  }

  public static String[] typeAndId(ItemStack item) {
    return nbt(item);
  }

  public static List<Material> canBreakBlocks(ItemStack item) {
    String[] found = typeAndId(item);
    if (found == null || isBlank(found[0]) || isBlank(found[1])) {
      return null;
    }
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return null;
    }
    try {
      Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
      Object plugin = mmoClass.getField("plugin").get(null);
      Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
      Object mmoType = typeClass.getMethod("get", String.class).invoke(null, found[0]);
      if (mmoType == null) {
        return null;
      }
      Object template = mmoClass.getMethod("getMMOItem", typeClass, String.class).invoke(plugin, mmoType, found[1]);
      if (template == null) {
        return null;
      }
      Class<?> statClass = Class.forName("net.Indyuce.mmoitems.stat.type.ItemStat");
      Object canBreakStat = Class.forName("net.Indyuce.mmoitems.ItemStats").getField("CAN_BREAK").get(null);
      Object data = template.getClass().getMethod("getData", statClass).invoke(template, canBreakStat);
      if (data == null) {
        return null;
      }
      if (!(data.getClass().getMethod("getList").invoke(data) instanceof List<?> names)) {
        return List.of();
      }
      List<Material> materials = new ArrayList<>();
      for (Object name : names) {
        Material material = Material.matchMaterial(String.valueOf(name));
        if (material != null && material.isBlock()) {
          materials.add(material);
        }
      }
      return materials;
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  public static boolean shouldKeepOnDeath(ItemStack item, Player owner) {
    if (item == null || item.getType().isAir() || owner == null) {
      return false;
    }
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return false;
    }
    try {
      Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
      Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, item);
      if (hasBooleanTag(nbtClass, nbt, "MMOITEMS_DISABLE_DEATH_DROP")) {
        return true;
      }
      if (!keepSoulboundOnDeath()) {
        return false;
      }
      if (!(boolean) nbtClass.getMethod("hasTag", String.class).invoke(nbt, "MMOITEMS_SOULBOUND")) {
        return false;
      }
      String soulbound = String.valueOf(nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_SOULBOUND"));
      return soulbound != null && soulbound.contains(owner.getUniqueId().toString());
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static boolean keepSoulboundOnDeath() {
    try {
      var plugin = Bukkit.getPluginManager().getPlugin("MMOItems");
      if (plugin == null) {
        return false;
      }
      return plugin.getConfig().getBoolean("soulbound.keep-on-death", true);
    } catch (Throwable ignored) {
      return true;
    }
  }

  private static boolean hasBooleanTag(Class<?> nbtClass, Object nbt, String tag) throws ReflectiveOperationException {
    if (!(boolean) nbtClass.getMethod("hasTag", String.class).invoke(nbt, tag)) {
      return false;
    }
    return (boolean) nbtClass.getMethod("getBoolean", String.class).invoke(nbt, tag);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank() || "null".equals(value);
  }

  private static String[] nbt(ItemStack item) {
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return null;
    }
    try {
      Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
      Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, item);
      return new String[]{
          String.valueOf(nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_TYPE")),
          String.valueOf(nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_ID"))
      };
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }
}
