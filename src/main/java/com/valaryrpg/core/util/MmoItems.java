package com.valaryrpg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Match MMOItems via MythicLib NBT, senza dipendenza compile-time.
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
        if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
            return false;
        }
        try {
            Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, item);
            String foundType = String.valueOf(nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_TYPE"));
            String foundId = String.valueOf(nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_ID"));
            boolean typeOk = type == null || type.isBlank() || type.equalsIgnoreCase(foundType);
            boolean idOk = id == null || id.isBlank() || id.equalsIgnoreCase(foundId);
            return typeOk && idOk;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
