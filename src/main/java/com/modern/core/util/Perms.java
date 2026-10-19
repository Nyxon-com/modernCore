package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import org.bukkit.command.CommandSender;

/**
 * Permessi letti da config.yml (permissions.*).
 */
public final class Perms {

    private Perms() {
    }

    public static String node(ModernCorePlugin plugin, String key) {
        return plugin.configs().root().getString("permissions." + key, "valary." + key);
    }

    public static boolean has(ModernCorePlugin plugin, CommandSender sender, String key) {
        return sender.hasPermission(node(plugin, key));
    }
}
