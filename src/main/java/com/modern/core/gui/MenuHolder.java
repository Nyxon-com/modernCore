package com.modern.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

  private final String menu;
  private final int page;
  private final String extra;
  private Inventory inventory;

  public MenuHolder(String menu) {
    this(menu, 1, "");
  }

  public MenuHolder(String menu, int page, String extra) {
    this.menu = menu;
    this.page = Math.max(1, page);
    this.extra = extra == null ? "" : extra;
  }

  public String menu() {
    return this.menu;
  }

  public int page() {
    return this.page;
  }

  public String extra() {
    return this.extra;
  }

  public void inventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return this.inventory;
  }
}
