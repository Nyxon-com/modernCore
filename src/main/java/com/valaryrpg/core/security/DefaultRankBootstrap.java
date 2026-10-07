package com.valaryrpg.core.security;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Assicura displayname/prefix del gruppo default (recluta) e grant player
 * su tutti i rank (default ha "* false", i rank superiori possono non ereditare).
 */
public final class DefaultRankBootstrap {

    private static final String RECLUTA_PREFIX =
            "<b><gradient:#C9D1DC:#8B8B9A>ʀᴇᴄʟᴜᴛᴀ</gradient></b> ";

    /** Rank player/staff da TAB + command-filter (owner ha gia' * true). */
    private static final List<String> PLAYER_GROUPS = List.of(
            "default",
            "kryndel",
            "vaelorn",
            "orynth",
            "nymira",
            "quirith",
            "eldryn",
            "volnaire",
            "iskara",
            "helper",
            "moderator",
            "admin"
    );

    private static final List<String> PLAYER_PERMS = List.of(
            "valary.tomb",
            "nyxonvaults.vault.1",
            "nyxonvaults.selector",
            "nyxonvaults.openremote",
            "valary.party",
            "valary.party.accept",
            "valary.trash",
            "valary.reciclatore",
            "nyxoncrates.open.*",
            "nyxoncrates.preview",
            // CommandPanels: perm: default -> commandpanel.panel.default (/infomob, /infomobterra, menu, ecc.)
            "commandpanel.panel.default",
            // /leveling (MMOCore attributes)
            "mmocore.attributes",
            // /changepassword <vecchia> <nuova> (AuthMe) — serve perche' default ha * false
            "authme.player.changepassword",
            // /spedizioni (la GUI controlla il rank: solo quirith+ possono partire)
            "valary.expeditions",
            // /vault buy e /diamantishop (NyxonVaults shop a diamanti)
            "nyxonvaults.buy"
    );

    private final ValaryCorePlugin plugin;

    public DefaultRankBootstrap(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void schedule() {
        Bukkit.getScheduler().runTaskLater(this.plugin, this::apply, 60L);
    }

    public void apply() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default setdisplayname recluta");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default setweight 1");
        // Rimuove prefisso legacy a priorità bassa, poi imposta quello gradient.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group default meta removeprefix 1");
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "lp group default meta setprefix 10 \"" + RECLUTA_PREFIX + "\""
        );
        for (String group : PLAYER_GROUPS) {
            for (String perm : PLAYER_PERMS) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "lp group " + group + " permission set " + perm + " true"
                );
            }
        }
    }
}
