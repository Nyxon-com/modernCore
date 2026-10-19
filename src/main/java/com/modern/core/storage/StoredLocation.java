package com.modern.core.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record StoredLocation(
        UUID owner,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public Location toLocation() {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public static StoredLocation from(UUID owner, String name, Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "" : world.getName();
        return new StoredLocation(
                owner,
                name,
                worldName,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }
}
