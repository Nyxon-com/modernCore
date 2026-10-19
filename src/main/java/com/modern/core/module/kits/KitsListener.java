package com.modern.core.module.kits;

import com.modern.core.gui.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * GUI kit: sinistro ritira, destro anteprima; cache claim e kit welcome.
 */
public final class KitsListener implements Listener {

    private final KitService kits;

    public KitsListener(KitService kits) {
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (KitService.PREVIEW.equals(holder.menu())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == event.getView().getTopInventory()) {
                this.kits.previewClick(player, event.getSlot(), holder);
            }
            return;
        }
        if (!KitService.MENU.equals(holder.menu())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        this.kits.click(player, event.getSlot(), holder.page(), event.getClick());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder
                && (KitService.MENU.equals(holder.menu()) || KitService.PREVIEW.equals(holder.menu()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.kits.prefetch(event.getPlayer());
        if (!event.getPlayer().hasPlayedBefore()) {
            this.kits.firstJoin(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.kits.forget(event.getPlayer());
    }
}
