package com.modern.core.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultima posizione prima di un teletrasporto (per /back).
 */
public final class LastLocations {

    private final Map<UUID, Location> last = new ConcurrentHashMap<>();

    public void remember(Player player) {
        if (player.getLocation().getWorld() != null) {
            put(player.getUniqueId(), player.getLocation());
        }
    }

    public void put(UUID uuid, Location location) {
        if (location != null && location.getWorld() != null) {
            this.last.put(uuid, location.clone());
        }
    }

    public Optional<Location> pull(UUID uuid) {
        Location location = this.last.remove(uuid);
        return location == null ? Optional.empty() : Optional.of(location);
    }

    public Optional<Location> peek(UUID uuid) {
        Location location = this.last.get(uuid);
        return location == null ? Optional.empty() : Optional.of(location.clone());
    }

    public void clear(UUID uuid) {
        this.last.remove(uuid);
    }
}
