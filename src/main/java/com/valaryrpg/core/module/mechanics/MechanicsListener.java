package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Eventi mechanics: morti, item a terra, long-jump, portali, holo-block.
 */
public final class MechanicsListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final DeathService deaths;
    private final GroundItemService groundItems;
    private final LongJumpService longJumps;
    private final PortalService portals;
    private final HoloBlockService holos;

    public MechanicsListener(
            ValaryCorePlugin plugin,
            DeathService deaths,
            GroundItemService groundItems,
            LongJumpService longJumps,
            PortalService portals,
            HoloBlockService holos
    ) {
        this.plugin = plugin;
        this.deaths = deaths;
        this.groundItems = groundItems;
        this.longJumps = longJumps;
        this.portals = portals;
        this.holos = holos;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        this.deaths.record(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathSnapshotClick(InventoryClickEvent event) {
        if (this.deaths.isSnapshot(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (item.isValid()) {
                this.groundItems.attach(item);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        // Prima del pickup: togli hologram / eventuali passenger residui.
        this.groundItems.remove(event.getItem().getUniqueId());
        for (var passenger : List.copyOf(event.getItem().getPassengers())) {
            event.getItem().removePassenger(passenger);
            passenger.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        this.groundItems.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        this.groundItems.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Item item) {
            this.groundItems.remove(item.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        this.longJumps.tryJump(event.getPlayer(), event.getClickedBlock().getType(), event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        var hand = event.getPlayer().getInventory().getItemInMainHand();
        if (this.portals.isWand(hand)) {
            event.setCancelled(true);
            this.portals.handleWand(
                    event.getPlayer(),
                    event.getClickedBlock(),
                    event.getPlayer().isSneaking(),
                    event.getAction() == Action.LEFT_CLICK_BLOCK
            );
            return;
        }
        if (this.holos.isWand(hand)) {
            event.setCancelled(true);
            this.holos.handleWand(event.getPlayer(), event.getClickedBlock());
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && this.holos.clickBlock(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHoloEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (this.holos.isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (this.holos.clickEntity(event.getPlayer(), event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        this.holos.onChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        this.groundItems.scanChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        this.holos.onChunkUnload(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.portals.rememberInside(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        this.portals.tryEnter(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        this.portals.tryEnter(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.longJumps.unload(event.getPlayer().getUniqueId());
        this.portals.unload(event.getPlayer().getUniqueId());
        this.holos.unload(event.getPlayer().getUniqueId());
    }
}
