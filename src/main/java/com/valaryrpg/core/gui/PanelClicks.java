package com.valaryrpg.core.gui;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Esegue click CommandPanels-like: [close] [console] [player] [sound] [message].
 */
public final class PanelClicks {

    private PanelClicks() {
    }

    public static boolean run(ValaryCorePlugin plugin, Player player, ConfigurationSection button) {
        if (button == null) {
            return false;
        }
        boolean close = false;
        Sounds.playRaw(plugin, player, button.getString("click-sound", ""), "gui.click-sound");
        for (String raw : button.getStringList("commands")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.replace("{player}", player.getName());
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("[close]") || lower.equals("close")) {
                close = true;
                continue;
            }
            if (lower.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring("[console]".length()).trim());
                continue;
            }
            if (lower.startsWith("[player]")) {
                player.performCommand(line.substring("[player]".length()).trim());
                continue;
            }
            if (lower.startsWith("[sound]")) {
                Sounds.playRaw(plugin, player, line.substring("[sound]".length()).trim(), "gui.command-sound");
                continue;
            }
            if (lower.startsWith("[message]")) {
                player.sendMessage(plugin.messages().deserialize(line.substring("[message]".length()).trim()));
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        if (close) {
            player.closeInventory();
        }
        return close;
    }

    public static void openSound(ValaryCorePlugin plugin, Player player, ConfigurationSection gui) {
        if (gui != null) {
            Sounds.playRaw(plugin, player, gui.getString("open-sound", ""), "gui.open-sound");
        }
    }
}
