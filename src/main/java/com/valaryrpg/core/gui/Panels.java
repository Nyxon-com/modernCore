package com.valaryrpg.core.gui;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Apre un chest GUI descritto da una sezione YAML `gui:` (title, rows, fill, slots).
 */
public final class Panels {

    private Panels() {
    }

    public static Inventory open(ValaryCorePlugin plugin, Player player, String menuId, ConfigurationSection gui, Map<String, String> titlePlaceholders) {
        int rows = GuiLayouts.rows(gui, 3);
        String titleRaw = gui == null ? "" : gui.getString("title", "");
        net.kyori.adventure.text.Component title;
        if (titleRaw == null || titleRaw.isBlank()) {
            title = plugin.messages().component(menuId + ".gui-title", titlePlaceholders == null ? Map.of() : titlePlaceholders);
        } else {
            String parsed = titleRaw;
            if (titlePlaceholders != null) {
                for (Map.Entry<String, String> entry : titlePlaceholders.entrySet()) {
                    parsed = parsed.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            parsed = parsed.replace("{player}", player.getName());
            title = plugin.messages().deserialize(parsed);
        }
        MenuHolder holder = new MenuHolder(menuId);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);
        ItemStack fill = PanelItems.filler(plugin, gui);
        if (!fill.getType().isAir()) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, fill.clone());
            }
        }
        ConfigurationSection slots = gui == null ? null : gui.getConfigurationSection("slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                ConfigurationSection button = slots.getConfigurationSection(key);
                if (button == null || "deposit".equalsIgnoreCase(button.getString("action", ""))) {
                    continue;
                }
                ItemStack icon = PanelItems.from(plugin, button, player);
                for (int slot : SlotSpec.expand(key)) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, icon.clone());
                    }
                }
            }
        }
        player.openInventory(inventory);
        PanelClicks.openSound(plugin, player, gui);
        return inventory;
    }
}
