package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Suoni da sounds/sounds.yml: ID namespaced, volume e pitch. Stringa vuota = silenzio.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(ValaryCorePlugin plugin, Player player, String key) {
        ConfigurationSection section = plugin.configs().sounds().getConfigurationSection(key);
        if (section != null) {
            playRaw(plugin, player, section.getString("sound"), (float) section.getDouble("volume", 1.0D), (float) section.getDouble("pitch", 1.0D), key);
            return;
        }
        String legacy = plugin.configs().sounds().getString(key);
        if (legacy == null || legacy.isBlank()) {
            legacy = plugin.configs().root().getString("sounds." + key);
        }
        playRaw(plugin, player, legacy, 1.0f, 1.0f, key);
    }

    public static void playRaw(ValaryCorePlugin plugin, Player player, String raw, String debugKey) {
        playRaw(plugin, player, raw, 1.0f, 1.0f, debugKey);
    }

    public static void playRaw(ValaryCorePlugin plugin, Player player, String raw, float volume, float pitch, String debugKey) {
        if (raw == null || raw.isBlank() || player == null) {
            return;
        }
        Location location = player.getLocation();
        String token = raw.trim();
        try {
            Sound sound = Sound.valueOf(token.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            player.playSound(location, sound, volume, pitch);
            return;
        } catch (IllegalArgumentException ignored) {
            // ID namespaced o pack custom
        }
        try {
            player.playSound(location, token, volume, pitch);
        } catch (Exception exception) {
            plugin.getLogger().warning("Suono sconosciuto in " + debugKey + ": " + token);
        }
    }
}
