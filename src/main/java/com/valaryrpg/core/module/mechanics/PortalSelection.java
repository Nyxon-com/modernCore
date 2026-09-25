package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.PanelItems;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wand e selezione pos1/pos2/dest in RAM, senza toccare YAML o regioni.
 */
final class PortalSelection {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> dest = new ConcurrentHashMap<>();
    private final Map<UUID, String> pos1World = new ConcurrentHashMap<>();
    private final Map<UUID, String> pos2World = new ConcurrentHashMap<>();
    private final Map<UUID, String> destWorld = new ConcurrentHashMap<>();

    PortalSelection(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    Location pos1(UUID uuid) {
        return this.pos1.get(uuid);
    }

    Location pos2(UUID uuid) {
        return this.pos2.get(uuid);
    }

    Location dest(UUID uuid) {
        return this.dest.get(uuid);
    }

    String pos1World(UUID uuid) {
        return this.pos1World.get(uuid);
    }

    String pos2World(UUID uuid) {
        return this.pos2World.get(uuid);
    }

    String destWorld(UUID uuid) {
        return this.destWorld.get(uuid);
    }

    void forget(UUID uuid) {
        this.pos1.remove(uuid);
        this.pos2.remove(uuid);
        this.dest.remove(uuid);
        this.pos1World.remove(uuid);
        this.pos2World.remove(uuid);
        this.destWorld.remove(uuid);
    }

    void markPos1(Player player) {
        markPos1(player, aimedBlock(player));
    }

    void markPos1(Player player, Location location) {
        Location block = blockOf(location);
        String world = worldName(block);
        if (world == null) {
            this.plugin.messages().send(player, "portals.world-unloaded");
            return;
        }
        this.pos1.put(player.getUniqueId(), block);
        this.pos1World.put(player.getUniqueId(), world);
        this.plugin.messages().send(player, "portals.pos1");
    }

    void markPos2(Player player) {
        markPos2(player, aimedBlock(player));
    }

    void markPos2(Player player, Location location) {
        Location block = blockOf(location);
        String world = worldName(block);
        if (world == null) {
            this.plugin.messages().send(player, "portals.world-unloaded");
            return;
        }
        this.pos2.put(player.getUniqueId(), block);
        this.pos2World.put(player.getUniqueId(), world);
        this.plugin.messages().send(player, "portals.pos2");
    }

    void markDest(Player player) {
        Location location = player.getLocation().clone();
        String world = worldName(location);
        if (world == null) {
            this.plugin.messages().send(player, "portals.world-unloaded");
            return;
        }
        this.dest.put(player.getUniqueId(), location);
        this.destWorld.put(player.getUniqueId(), world);
        this.plugin.messages().send(player, "portals.dest");
    }

    void giveWand(Player player) {
        ConfigurationSection wand = this.plugin.configs().mechanics().getConfigurationSection("portal-wand");
        ItemStack item = wand == null
                ? PanelItems.named("BLAZE_ROD", Material.BLAZE_ROD)
                : PanelItems.from(this.plugin, wand, player);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1));
        player.getInventory().addItem(item);
        this.plugin.messages().send(player, "portals.wand-given");
    }

    boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey(), PersistentDataType.BYTE);
    }

    void handleWand(Player player, Block block, boolean sneak, boolean leftClick) {
        Location location = block.getLocation();
        if (leftClick && sneak) {
            markDest(player);
            return;
        }
        if (sneak) {
            markPos2(player, location);
            return;
        }
        markPos1(player, location);
    }

    private NamespacedKey wandKey() {
        return new NamespacedKey(this.plugin, "portal_wand");
    }

    private static Location aimedBlock(Player player) {
        Block block = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        return block == null ? player.getLocation() : block.getLocation();
    }

    private static Location blockOf(Location location) {
        if (location == null || !location.isWorldLoaded()) {
            return location == null ? null : location.clone();
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String worldName(Location location) {
        if (location == null || !location.isWorldLoaded()) {
            return null;
        }
        return location.getWorld().getName();
    }
}
