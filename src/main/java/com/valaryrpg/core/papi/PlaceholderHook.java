package com.valaryrpg.core.papi;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;

/**
 * Carica l'expansion PlaceholderAPI solo se il plugin è presente (niente dipendenza runtime).
 */
public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(ValaryCorePlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            Class<?> expansion = Class.forName("com.valaryrpg.core.papi.ValaryExpansion");
            Object instance = expansion.getConstructor(ValaryCorePlugin.class).newInstance(plugin);
            Object registered = expansion.getMethod("register").invoke(instance);
            if (registered instanceof Boolean ok && ok) {
                plugin.getLogger().info("PlaceholderAPI: expansion valary registrata.");
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("PlaceholderAPI hook fallito: " + exception.getMessage());
        }
    }
}
