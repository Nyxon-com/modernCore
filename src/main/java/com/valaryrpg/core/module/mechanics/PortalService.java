package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.MmoItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

public final class PortalService {

    private final ValaryCorePlugin plugin;

    public PortalService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void tryEnter(Player player, Location from, Location to) {
        if (to.getWorld() == null) {
            return;
        }
        String entered = nameAt(to);
        if (entered == null || entered.equals(nameAt(from))) {
            return;
        }
        ConfigurationSection portal = this.plugin.configs().mechanics().getConfigurationSection("portals." + entered);
        if (portal == null || !portal.getBoolean("enabled", true)) {
            return;
        }
        String permission = portal.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            this.plugin.messages().send(player, "portals.no-permission", Map.of("portal", entered));
            return;
        }
        ConfigurationSection item = portal.getConfigurationSection("item");
        if (item != null && !consumeOrMatch(player, item)) {
            this.plugin.messages().send(player, "portals.need-item", Map.of("portal", entered));
            return;
        }
        Location destination = destinationOf(portal);
        if (destination == null) {
            this.plugin.messages().send(player, "portals.invalid-destination", Map.of("portal", entered));
            return;
        }
        int warmup = portal.getInt("warmup-seconds", 0);
        if (warmup > 0) {
            this.plugin.messages().send(player, "portals.warmup", Map.of(
                    "portal", entered,
                    "seconds", String.valueOf(warmup)
            ));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(destination);
                }
            }, warmup * 20L);
        } else {
            player.teleport(destination);
        }
        this.plugin.messages().send(player, "portals.used", Map.of("portal", entered));
    }

    private String nameAt(Location location) {
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        if (portals == null || location.getWorld() == null) {
            return null;
        }
        for (String name : portals.getKeys(false)) {
            ConfigurationSection portal = portals.getConfigurationSection(name);
            if (portal != null && portal.getBoolean("enabled", true) && inside(portal, location)) {
                return name;
            }
        }
        return null;
    }

    private boolean inside(ConfigurationSection portal, Location location) {
        String worldName = portal.getString("world", "");
        World world = location.getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        ConfigurationSection pos1 = portal.getConfigurationSection("pos1");
        ConfigurationSection pos2 = portal.getConfigurationSection("pos2");
        if (pos1 == null || pos2 == null) {
            return false;
        }
        double minX = Math.min(pos1.getDouble("x"), pos2.getDouble("x"));
        double maxX = Math.max(pos1.getDouble("x"), pos2.getDouble("x"));
        double minY = Math.min(pos1.getDouble("y"), pos2.getDouble("y"));
        double maxY = Math.max(pos1.getDouble("y"), pos2.getDouble("y"));
        double minZ = Math.min(pos1.getDouble("z"), pos2.getDouble("z"));
        double maxZ = Math.max(pos1.getDouble("z"), pos2.getDouble("z"));
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX + 1.0D && y >= minY && y <= maxY + 1.0D && z >= minZ && z <= maxZ + 1.0D;
    }

    private Location destinationOf(ConfigurationSection portal) {
        ConfigurationSection dest = portal.getConfigurationSection("destination");
        if (dest == null) {
            return null;
        }
        World world = Bukkit.getWorld(dest.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                dest.getDouble("x"),
                dest.getDouble("y"),
                dest.getDouble("z"),
                (float) dest.getDouble("yaw"),
                (float) dest.getDouble("pitch")
        );
    }

    private boolean consumeOrMatch(Player player, ConfigurationSection item) {
        Material material = Material.matchMaterial(item.getString("material", "AIR"));
        String type = item.getString("mmoitem-type", "");
        String id = item.getString("mmoitem-id", "");
        int amount = Math.max(1, item.getInt("amount", 1));
        PlayerInventory inventory = player.getInventory();
        int slot = findSlot(inventory, material, type, id, amount);
        if (slot < 0) {
            return false;
        }
        if (!item.getBoolean("consume", true)) {
            return true;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null) {
            return false;
        }
        int left = stack.getAmount() - amount;
        if (left <= 0) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(left);
        }
        return true;
    }

    private int findSlot(PlayerInventory inventory, Material material, String type, String id, int amount) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir() || stack.getAmount() < amount) {
                continue;
            }
            if (material != null && material != Material.AIR && stack.getType() != material) {
                continue;
            }
            if (!MmoItems.matches(stack, type, id)) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
