package com.valaryrpg.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder per distinguere i chest GUI Valary senza metadati sugli item.
 */
public final class MenuHolder implements InventoryHolder {

    private final String menu;
    private Inventory inventory;

    public MenuHolder(String menu) {
        this.menu = menu;
    }

    public String menu() {
        return this.menu;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
