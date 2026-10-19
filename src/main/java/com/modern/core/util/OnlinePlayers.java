package com.modern.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class OnlinePlayers {

    private OnlinePlayers() {
    }

    public static Player exact(String name) {
        return Bukkit.getPlayerExact(name);
    }

    public static boolean suggest(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(online.getName());
            }
        }
        return true;
    }
}
