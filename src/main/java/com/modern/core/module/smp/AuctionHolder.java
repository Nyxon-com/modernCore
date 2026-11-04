package com.modern.core.module.smp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AuctionHolder implements InventoryHolder {
  private final int page;
  private final boolean mine;

  public AuctionHolder(int page, boolean mine) {
    this.page = page;
    this.mine = mine;
  }

  public int page() {
    return this.page;
  }

  public boolean mine() {
    return this.mine;
  }

  @Override
  public Inventory getInventory() {
    return null;
  }
}
