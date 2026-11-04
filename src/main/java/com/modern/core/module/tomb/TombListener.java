package com.modern.core.module.tomb;

import com.modern.core.gui.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class TombListener implements Listener {

  private final TombService tombs;

  public TombListener(TombService tombs) {
    this.tombs = tombs;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDeath(PlayerDeathEvent event) {
    this.tombs.handleDeath(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeathMonitor(PlayerDeathEvent event) {
    this.tombs.ensureNoGroundDrops(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    this.tombs.maybeAutoOpen(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
      return;
    }
    if (TombMenus.LIST.equals(holder.menu())) {
      event.setCancelled(true);
      if (event.getClickedInventory() == event.getView().getTopInventory()) {
        this.tombs.menus().clickList(player, event.getSlot(), holder.page());
      }
      return;
    }
    if (TombMenus.VIEW.equals(holder.menu())) {
      event.setCancelled(true);
      if (event.getClickedInventory() == event.getView().getTopInventory()) {
        this.tombs.menus().clickView(player, event.getSlot(), holder);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
      return;
    }
    if (TombMenus.LIST.equals(holder.menu()) || TombMenus.VIEW.equals(holder.menu())) {
      event.setCancelled(true);
    }
  }
}
