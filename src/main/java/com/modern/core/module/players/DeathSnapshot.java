package com.modern.core.module.players;

import com.modern.core.storage.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Ultima morte: posizione, causa, killer e inventario serializzato.
 */
public record DeathSnapshot(
        UUID uuid,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long at,
        String cause,
        String killer,
        String inventory
) {

    public Location toLocation() {
        World loaded = Bukkit.getWorld(this.world);
        if (loaded == null) {
            return null;
        }
        return new Location(loaded, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public StoredLocation toStored() {
        return new StoredLocation(this.uuid, "death", this.world, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}
