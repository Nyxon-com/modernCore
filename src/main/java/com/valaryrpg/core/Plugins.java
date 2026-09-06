package com.valaryrpg.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Accesso lazy al plugin (i comandi vanilla vengono registrati in bootstrap).
 */
public final class Plugins {

    private Plugins() {
    }

    public static ValaryCorePlugin core() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ValaryRPG-Core");
        if (plugin instanceof ValaryCorePlugin core && core.isEnabled()) {
            return core;
        }
        return null;
    }
}
