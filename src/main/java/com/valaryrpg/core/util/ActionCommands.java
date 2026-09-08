package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Esegue liste di comandi YAML: [close] [console] [player] [sound] [message] oppure console.
 */
public final class ActionCommands {

    private ActionCommands() {
    }

    public static boolean run(ValaryCorePlugin plugin, Player player, List<String> lines) {
        return run(plugin, player, lines, Map.of());
    }

    public static boolean run(ValaryCorePlugin plugin, Player player, List<String> lines, Map<String, String> extra) {
        if (lines == null || lines.isEmpty() || player == null) {
            return false;
        }
        boolean close = false;
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.replace("{player}", player.getName());
            if (player.getWorld() != null) {
                line = line.replace("{world}", player.getWorld().getName());
            }
            line = line.replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                    .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                    .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
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
                Sounds.playRaw(plugin, player, line.substring("[sound]".length()).trim(), "action.sound");
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
}
