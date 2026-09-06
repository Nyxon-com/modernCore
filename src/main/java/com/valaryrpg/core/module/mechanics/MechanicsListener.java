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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MechanicsListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final DeathService deaths;
    private final GroundItemService groundItems;
    private final LongJumpService longJumps;
    private final PortalService portals;

    public MechanicsListener(
            ValaryCorePlugin plugin,
            DeathService deaths,
            GroundItemService groundItems,
            LongJumpService longJumps,
            PortalService portals
    ) {
        this.plugin = plugin;
        this.deaths = deaths;
        this.groundItems = groundItems;
        this.longJumps = longJumps;
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        this.deaths.record(event.getPlayer());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        this.groundItems.remove(event.getItem().getUniqueId());
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
        this.longJumps.tryJump(event.getPlayer(), event.getClickedBlock().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        this.portals.tryEnter(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.longJumps.unload(event.getPlayer().getUniqueId());
    }
}
