package com.valaryrpg.core.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI se presente; toglie i %placeholder% non risolti.
 */
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
