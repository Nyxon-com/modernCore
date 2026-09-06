package com.valaryrpg.core.module.kits;

import com.valaryrpg.core.gui.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * GUI kit e kit di benvenuto al primo join.
 */
public final class KitsListener implements Listener {

    private final KitService kits;

    public KitsListener(KitService kits) {
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !KitService.MENU.equals(holder.menu())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        this.kits.click(player, event.getSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPlayedBefore()) {
            this.kits.firstJoin(event.getPlayer());
        }
    }
}
