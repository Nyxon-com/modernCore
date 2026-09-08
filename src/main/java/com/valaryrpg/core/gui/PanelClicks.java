package com.valaryrpg.core.gui;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.Sounds;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

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
        Sounds.playRaw(plugin, player, button.getString("click-sound", ""), "gui.click-sound");
        return ActionCommands.run(plugin, player, button.getStringList("commands"));
    }

    public static void openSound(ValaryCorePlugin plugin, Player player, ConfigurationSection gui) {
        if (gui != null) {
            Sounds.playRaw(plugin, player, gui.getString("open-sound", ""), "gui.open-sound");
        }
    }
}
