package com.valaryrpg.core.papi;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.module.worlds.WorldsModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder RAM-only: %valary_nick%, %valary_god%, %valary_vanished%, %valary_world%, %valary_world_alias%, %valary_fly%.
 */
public final class ValaryExpansion extends PlaceholderExpansion {

    private final ValaryCorePlugin plugin;

    public ValaryExpansion(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "valary";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ValaryRPG";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Player online = player.getPlayer();
        return switch (params.toLowerCase()) {
            case "nick" -> nick(online, player);
            case "god" -> flag(online != null && god(online));
            case "vanished" -> flag(online != null && vanished(online));
            case "fly" -> flag(online != null && online.getAllowFlight());
            case "world" -> online == null || online.getWorld() == null ? "" : online.getWorld().getName();
            case "world_alias" -> alias(online);
            default -> null;
        };
    }

    private String nick(Player online, OfflinePlayer offline) {
        if (online != null) {
            return PlainTextComponentSerializer.plainText().serialize(online.displayName());
        }
        String name = offline.getName();
        return name == null ? "" : name;
    }

    private boolean god(Player player) {
        PlayersModule module = this.plugin.modules().get("players");
        return module != null && module.god() != null && module.god().isGod(player);
    }

    private boolean vanished(Player player) {
        ModerationModule module = this.plugin.modules().get("moderation");
        return module != null && module.vanish() != null && module.vanish().isVanished(player);
    }

    private String alias(Player online) {
        if (online == null || online.getWorld() == null) {
            return "";
        }
        WorldsModule module = this.plugin.modules().get("worlds");
        if (module == null || module.catalog() == null) {
            return online.getWorld().getName();
        }
        return module.catalog().alias(online.getWorld());
    }

    private static String flag(boolean value) {
        return value ? "true" : "false";
    }
}
