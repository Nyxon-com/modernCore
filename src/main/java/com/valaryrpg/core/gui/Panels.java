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
        return open(plugin, player, new MenuHolder(menuId), gui, gui, titlePlaceholders);
    }

    public static Inventory open(
            ValaryCorePlugin plugin,
            Player player,
            MenuHolder holder,
            ConfigurationSection gui,
            ConfigurationSection fillFrom,
            Map<String, String> titlePlaceholders
    ) {
        ConfigurationSection layout = gui == null ? fillFrom : gui;
        ConfigurationSection fillSection = fillFrom == null ? layout : fillFrom;
        int rows = GuiLayouts.rows(layout, GuiLayouts.rows(fillSection, 3));
        String titleRaw = firstTitle(layout, fillSection);
        net.kyori.adventure.text.Component title;
        if (titleRaw == null || titleRaw.isBlank()) {
            title = plugin.messages().component(holder.menu() + ".gui-title", titlePlaceholders == null ? Map.of() : titlePlaceholders);
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
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);
        ItemStack fill = PanelItems.filler(plugin, fillSection);
        if (!fill.getType().isAir()) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, fill.clone());
            }
        }
        PanelFrames.apply(plugin, inventory, fillSection);
        if (layout != fillSection) {
            PanelFrames.apply(plugin, inventory, layout);
        }
        ConfigurationSection slots = layout == null ? null : layout.getConfigurationSection("slots");
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
        PanelClicks.openSound(plugin, player, fillSection);
        return inventory;
    }

    private static String firstTitle(ConfigurationSection layout, ConfigurationSection fallback) {
        if (layout != null) {
            String title = layout.getString("title", "");
            if (title != null && !title.isBlank()) {
                return title;
            }
        }
        return fallback == null ? "" : fallback.getString("title", "");
    }
}
