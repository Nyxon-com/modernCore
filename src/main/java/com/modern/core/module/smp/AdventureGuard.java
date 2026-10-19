package com.modern.core.module.smp;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

public final class AdventureGuard implements Listener {

  @EventHandler
  public void onOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player player) || player.getGameMode() != GameMode.ADVENTURE) {
      return;
    }
    InventoryType type = event.getInventory().getType();
    if (type != InventoryType.CHEST && type != InventoryType.BARREL
        && type != InventoryType.SHULKER_BOX && type != InventoryType.ENDER_CHEST) {
      return;
    }
    event.setCancelled(true);
    SmpText.send(player, "&cAllo spawn non puoi aprire le casse.");
  }
}
