package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulizia argomenti comando: virgolette, token, nomi home/warp.
 */
public final class CommandText {

    private CommandText() {
    }

    public static String unwrap(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    public static boolean hasQuotes(String raw) {
        String value = unwrap(raw);
        return value.indexOf('"') >= 0 || value.indexOf('\'') >= 0;
    }

    public static List<String> tokens(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return tokens;
        }
        for (String piece : raw.trim().split("\\s+")) {
            if (!piece.isBlank()) {
                tokens.add(unwrap(piece));
            }
        }
        return tokens;
    }

    public static String firstToken(String raw) {
        List<String> tokens = tokens(raw);
        return tokens.isEmpty() ? "" : tokens.getFirst();
    }

    public static boolean hasStrayQuotes(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String piece : raw.trim().split("\\s+")) {
            if (hasQuotes(piece)) {
                return true;
            }
        }
        return false;
    }

    public static String nameOrNull(CommandSender sender, ValaryCorePlugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (hasStrayQuotes(raw)) {
            plugin.messages().send(sender, "general.quotes");
            return null;
        }
        return unwrap(raw).toLowerCase(Locale.ROOT);
    }
}
