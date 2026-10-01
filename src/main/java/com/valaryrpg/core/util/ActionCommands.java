package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Esegue liste di comandi YAML: [close] [console] [player] [sound] [message] [delay].
 * Senza tag: console (kit/sell) oppure player se {@code playerDefault}.
 * {@code [delay] N} = aspetta N tick prima dei comandi successivi (stessa lista).
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
        return runFrom(plugin, player, lines, extra, playerDefault, 0);
    }

    private static boolean runFrom(
            ValaryCorePlugin plugin,
            Player player,
            List<String> lines,
            Map<String, String> extra,
            boolean playerDefault,
            int start
    ) {
        boolean close = false;
        for (int i = start; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = fill(player, raw, extra);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("[close]") || lower.equals("close")) {
                close = true;
                continue;
            }
            if (lower.startsWith("[delay]")) {
                long ticks = parseDelay(line.substring("[delay]".length()).trim());
                int next = i + 1;
                if (ticks > 0 && next < lines.size()) {
                    Tasks.later(plugin, ticks, () -> {
                        if (player.isOnline()) {
                            runFrom(plugin, player, lines, extra, playerDefault, next);
                        }
                    });
                    return close;
                }
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

    private static long parseDelay(String raw) {
        try {
            return Math.max(0L, Long.parseLong(raw.trim().split("\\s+")[0]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            return 0L;
        }
    }
    private static String fill(Player player, String raw, Map<String, String> extra) {
        String line = raw
                .replace("{player}", player.getName())
                .replace("{player_name}", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName());
        if (player.getWorld() != null) {
            line = line.replace("{world}", player.getWorld().getName());
        }
        line = line.replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
        for (Map.Entry<String, String> entry : extra.entrySet()) {
            line = line.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return Placeholders.apply(player, line);
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
