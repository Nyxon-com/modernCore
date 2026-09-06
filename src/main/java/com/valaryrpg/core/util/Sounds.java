package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Suoni nominati in config.yml (sounds.*).
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(ValaryCorePlugin plugin, Player player, String key) {
        String raw = plugin.configs().root().getString("sounds." + key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(raw);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Suono sconosciuto in sounds." + key + ": " + raw);
        }
    }
}
