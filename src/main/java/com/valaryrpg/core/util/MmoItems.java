package com.valaryrpg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * MMOItems via MythicLib/MMOItems reflection: match, icona GUI, give da comando YAML.
 */
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
