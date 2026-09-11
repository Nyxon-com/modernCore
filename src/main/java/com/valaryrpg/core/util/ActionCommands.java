package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Esegue liste di comandi YAML: [close] [console] [player] [sound] [message].
 * Senza tag: console (kit/sell) oppure player se {@code playerDefault}.
 */
public final class ActionCommands {

    private ActionCommands() {
    }

    public static boolean run(ValaryCorePlugin plugin, Player player, List<String> lines) {
        return run(plugin, player, lines, Map.of(), false);
    }

    public static boolean run(ValaryCorePlugin plugin, Player player, List<String> lines, Map<String, String> extra) {
        return run(plugin, player, lines, extra, false);
    }

    public static boolean run(
            ValaryCorePlugin plugin,
            Player player,
            List<String> lines,
            Map<String, String> extra,
            boolean playerDefault
    ) {
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
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(line.substring("[console]".length())));
                continue;
            }
            if (lower.startsWith("[player]")) {
                asPlayer(player, command(line.substring("[player]".length())));
                continue;
            }
            if (lower.startsWith("[sound]")) {
                Sounds.playRaw(plugin, player, line.substring("[sound]".length()).trim(), "action.sound");
                continue;
            }
            if (lower.startsWith("[message]") || lower.startsWith("[msg]")) {
                int cut = lower.startsWith("[message]") ? "[message]".length() : "[msg]".length();
                player.sendMessage(plugin.messages().deserialize(line.substring(cut).trim()));
                continue;
            }
            String cmd = command(line);
            if (playerDefault) {
                asPlayer(player, cmd);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
        if (close) {
            player.closeInventory();
        }
        return close;
    }

    private static void asPlayer(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        if (Bukkit.dispatchCommand(player, command)) {
            return;
        }
        player.performCommand(command);
    }

    private static String command(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("/")) {
            return value.substring(1).trim();
        }
        return value;
    }
}
