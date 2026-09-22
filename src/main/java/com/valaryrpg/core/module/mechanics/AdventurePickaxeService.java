package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.MmoItems;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;

/**
 * Nasconde la lore vanilla "Can break" sui picconi MMOItems.
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
    }

    public void stamp(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (!item.getType().name().endsWith("_PICKAXE")) {
            return;
        }
        if (!MmoItems.matches(item, "TOOL", null)) {
            return;
        }
        if (!item.hasData(DataComponentTypes.CAN_BREAK)) {
            return;
        }
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stampInventory(player);
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                stampInventory(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        stamp(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        stamp(event.getCurrentItem());
        stamp(event.getCursor());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            stampInventory(player);
        }
    }
}
