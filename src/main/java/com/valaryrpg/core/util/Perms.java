package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.command.CommandSender;

/**
 * Permessi letti da config.yml (permissions.*).
 */
public final class Perms {

    private Perms() {
    }

    public static String node(ValaryCorePlugin plugin, String key) {
        return plugin.configs().root().getString("permissions." + key, "valary." + key);
    }

    public static boolean has(ValaryCorePlugin plugin, CommandSender sender, String key) {
        return sender.hasPermission(node(plugin, key));
    }
}
