package com.valaryrpg.core.papi;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.module.worlds.WorldsModule;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder RAM-only: %valary_nick%, %valary_nick_mm%, %valary_god%, %valary_vanished%,
 * %valary_world%, %valary_world_alias%, %valary_fly%,
 * %valary_hp_bar%, %valary_mana_bar%, %valary_level_bar%.
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
            case "nick_mm" -> nickMini(online, player);
            case "god" -> flag(online != null && god(online));
            case "vanished" -> flag(online != null && vanished(online));
            case "fly" -> flag(online != null && online.getAllowFlight());
            case "world" -> online == null || online.getWorld() == null ? "" : online.getWorld().getName();
            case "world_alias" -> alias(online);
            case "hp_bar" -> online == null ? "" : nexoBar(online, "fh_hp", hpRatio(online));
            case "mana_bar" -> online == null ? "" : nexoBar(online, "fh_mana", manaRatio(online));
            case "level_bar" -> online == null ? "" : nexoBar(online, "fh_level", levelRatio(online));
            default -> null;
        };
    }

    private static double hpRatio(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20.0D : max.getValue();
        if (maxHp <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, player.getHealth() / maxHp));
    }

    private static double manaRatio(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return 0.0D;
        }
        String curRaw = PlaceholderAPI.setPlaceholders(player, "%mmocore_mana%");
        String maxRaw = PlaceholderAPI.setPlaceholders(player, "%mmocore_max_mana%");
        double cur = parseDouble(curRaw);
        double max = parseDouble(maxRaw);
        if (max <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, cur / max));
    }

    private static double levelRatio(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return 0.0D;
        }
        // 0..100 percent → 0..1
        double pct = parseDouble(PlaceholderAPI.setPlaceholders(player, "%mmocore_level_percent%"));
        if (pct > 1.0D) {
            pct = pct / 100.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, pct));
    }

    /** Fantasy HUD bars use glyphs fh_*_1 .. fh_*_25 (empty when ratio ~ 0). */
    private static String nexoBar(Player player, String prefix, double ratio) {
        int index = (int) Math.round(ratio * 25.0D);
        if (index <= 0) {
            return "";
        }
        if (index > 25) {
            index = 25;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return "";
        }
        return PlaceholderAPI.setPlaceholders(player, "%nexo_" + prefix + "_" + index + "%");
    }

    private static double parseDouble(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("%")) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(raw.replace(",", ".").trim());
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private String nick(Player online, OfflinePlayer offline) {
        if (online != null) {
            PlayersModule module = this.plugin.modules().get("players");
            if (module != null && module.nicks() != null) {
                return PlainTextComponentSerializer.plainText().serialize(module.nicks().displayComponent(online));
            }
            return PlainTextComponentSerializer.plainText().serialize(online.displayName());
        }
        String name = offline.getName();
        return name == null ? "" : name;
    }

    private String nickMini(Player online, OfflinePlayer offline) {
        if (online != null) {
            PlayersModule module = this.plugin.modules().get("players");
            if (module != null && module.nicks() != null) {
                return module.nicks().displayMini(online);
            }
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
