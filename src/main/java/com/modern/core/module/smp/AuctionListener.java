package com.modern.core.module.smp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AuctionListener implements Listener {
  private final AuctionService auctions;

  public AuctionListener(AuctionService auctions) {
    this.auctions = auctions;
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof AuctionHolder)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (event.getClickedInventory() == event.getInventory()) {
      this.auctions.click(player, event.getInventory(), event.getSlot());
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof AuctionHolder) {
      event.setCancelled(true);
    }
  }
}
