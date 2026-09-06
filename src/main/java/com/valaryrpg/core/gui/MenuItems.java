package com.valaryrpg.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Icone GUI da materiali YAML (filler, pulsanti).
 */
public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack filler(String materialName) {
        ItemStack item = named(materialName, Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    public static ItemStack named(String materialName, Material fallback) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        return new ItemStack(material == null ? fallback : material);
    }
}
