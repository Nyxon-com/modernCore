package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;

/**
 * Nasconde la lore vanilla "Can break" sui picconi.
 */
public final class AdventurePickaxeService implements Listener {

    private final ValaryCorePlugin plugin;

    public AdventurePickaxeService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void stampInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            stamp(item);
        }
        stamp(inventory.getItemInOffHand());
        stamp(player.getItemOnCursor());
    }

    public void stamp(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (!item.getType().name().endsWith("_PICKAXE")) {
            return;
        }
        item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_DESTROYS));
        Set<DataComponentType> hidden = new HashSet<>();
        if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) {
            hidden.addAll(item.getData(DataComponentTypes.TOOLTIP_DISPLAY).hiddenComponents());
        }
        hidden.add(DataComponentTypes.CAN_BREAK);
        item.setData(
                DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().hiddenComponents(hidden).build()
        );
    }

    private void later(Player player) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                stampInventory(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        later(player);
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                stampInventory(player);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            later(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            later(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            later(player);
        }
    }
}
