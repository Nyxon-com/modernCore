package com.valaryrpg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Passa il lavoro I/O sul pool async e torna sul main thread per Bukkit API.
 */
public final class Tasks {

    private Tasks() {
    }

    public static void async(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runQuiet(plugin, task));
    }

    public static void sync(Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            runQuiet(plugin, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> runQuiet(plugin, task));
    }

    public static void nextTick(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, () -> runQuiet(plugin, task));
    }

    public static void later(Plugin plugin, long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> runQuiet(plugin, task), Math.max(1L, ticks));
    }

    private static void runQuiet(Plugin plugin, Runnable task) {
        try {
            task.run();
        } catch (Exception exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }
}
