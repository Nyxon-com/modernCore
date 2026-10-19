package com.modern.core.module.expeditions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class ExpeditionsListener implements Listener {

    private final ExpeditionService service;
    private final ExpeditionMenu menu;
    private final ExpeditionClaimMenu claimMenu;

    ExpeditionsListener(ExpeditionService service, ExpeditionMenu menu) {
        this.service = service;
        this.menu = menu;
        this.claimMenu = service.claimMenu();
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (this.claimMenu.isMenu(event.getInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == event.getView().getTopInventory()
                    && event.getSlot() == this.claimMenu.buttonSlot()) {
                this.service.claimAll(player);
            }
            return;
        }
        if (!this.menu.isMenu(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        this.menu.click(player, event.getSlot());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (this.menu.isMenu(event.getInventory()) || this.claimMenu.isMenu(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /** Chiudere la GUI di riscatto (anche a metà animazione) consegna le ricompense. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && this.claimMenu.isMenu(event.getInventory())) {
            this.service.closeClaim(player, event.getReason() == InventoryCloseEvent.Reason.DISCONNECT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.service.load(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // il player è ancora online qui: consegna sincrona se aveva un riscatto aperto
        this.service.closeClaim(event.getPlayer(), true);
        this.service.forget(event.getPlayer().getUniqueId());
    }
}
