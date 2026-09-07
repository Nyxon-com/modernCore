package com.valaryrpg.core.util;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Warmup teletrasporto condiviso tra spawn, home e warp. Delay e cancel-on-move da YAML.
 */
public final class Teleports {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Location> origins = new ConcurrentHashMap<>();

    public Teleports(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void go(Player player, Location destination, int warmupSeconds, boolean cancelOnMove, Consumer<Player> after) {
        if (warmupSeconds <= 0) {
            complete(player, destination, after);
            return;
        }
        this.plugin.messages().send(player, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
        this.origins.put(player.getUniqueId(), player.getLocation().clone());
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Location origin = this.origins.remove(player.getUniqueId());
            if (origin == null || !player.isOnline()) {
                return;
            }
            if (cancelOnMove && moved(origin, player.getLocation())) {
                this.plugin.messages().send(player, "teleport.cancelled-move");
                return;
            }
            complete(player, destination, after);
        }, warmupSeconds * 20L);
    }

    public void cancel(UUID uuid) {
        this.origins.remove(uuid);
    }

    private void complete(Player player, Location destination, Consumer<Player> after) {
        this.plugin.lastLocations().remember(player);
        player.teleportAsync(destination).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success) || !player.isOnline()) {
                return;
            }
            Sounds.play(this.plugin, player, "teleport-success");
            if (after != null) {
                after.accept(player);
            }
        });
    }

    public static boolean moved(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return true;
        }
        return from.toVector().distanceSquared(to.toVector()) > 0.04D;
    }
}
