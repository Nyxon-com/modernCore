package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Sounds;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * /back: ultima posizione prima di un teletrasporto, oppure morte se abilitato in YAML.
 */
public final class BackService {

    private final ModernCorePlugin plugin;

    public BackService(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void go(Player player) {
        Optional<Location> last = this.plugin.lastLocations().pull(player.getUniqueId());
        if (last.isEmpty()) {
            this.plugin.messages().send(player, "back.none");
            return;
        }
        Location destination = last.get();
        player.teleportAsync(destination).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success) || !player.isOnline()) {
                return;
            }
            this.plugin.messages().send(player, "back.teleported");
            Sounds.play(this.plugin, player, "teleport-success");
        });
    }
}
