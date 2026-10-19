package com.modern.core.module.holograms;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Testo hologram: &-codes FancyHolograms + MiniMessage + PlaceholderAPI.
 */
final class HologramTexts {

    private static final Pattern HASH = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern HEX_X = Pattern.compile("&x((?:&[0-9A-Fa-f]){6})", Pattern.CASE_INSENSITIVE);
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private HologramTexts() {
    }

    static Component parse(ModernCorePlugin plugin, Player viewer, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Component.empty();
        }
        List<String> mini = new ArrayList<>();
        for (String line : lines) {
            String converted = toMiniMessage(line);
            mini.add(Placeholders.apply(viewer, converted));
        }
        return plugin.messages().deserialize(String.join("<newline>", mini));
    }

    static boolean personal(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (line != null && line.contains("%")) {
                return true;
            }
        }
        return false;
    }

    static String toMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw.replace('\u00A7', '&');
        Matcher hexX = HEX_X.matcher(value);
        StringBuilder hexed = new StringBuilder();
        while (hexX.find()) {
            String digits = hexX.group(1).replace("&", "");
            hexX.appendReplacement(hexed, "<#" + digits + ">");
        }
        hexX.appendTail(hexed);
        value = HASH.matcher(hexed.toString()).replaceAll("<#$1>");
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' && index + 1 < value.length()) {
                String tag = CODES.get(Character.toLowerCase(value.charAt(index + 1)));
                if (tag != null) {
                    out.append(tag);
                    index++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }
}
