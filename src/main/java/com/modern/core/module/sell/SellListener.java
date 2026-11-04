package com.modern.core.module.sell;

import com.modern.core.gui.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class SellListener implements Listener {

  private final SellService sell;

  public SellListener(SellService sell) {
    this.sell = sell;
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !SellService.MENU.equals(holder.menu())) {
      return;
    }
    if (!(event.getWhoClicked() instanceof Player player)) {
      event.setCancelled(true);
      return;
    }
    int raw = event.getRawSlot();
    if (raw >= 0 && raw < event.getInventory().getSize() && this.sell.locked(raw)) {
      event.setCancelled(true);
      this.sell.click(player, raw, event.getInventory());
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !SellService.MENU.equals(holder.menu())) {
      return;
    }
    if (event.getRawSlots().stream().anyMatch(this.sell::locked)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !SellService.MENU.equals(holder.menu())) {
      return;
    }
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    if (this.sell.sellOnClose()) {
      this.sell.sell(player, event.getInventory(), true);
      return;
    }
    this.sell.refund(player, event.getInventory());
  }
}
