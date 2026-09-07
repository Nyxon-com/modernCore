package com.valaryrpg.core.module.sell;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.GuiLayouts;
import com.valaryrpg.core.gui.PanelClicks;
import com.valaryrpg.core.gui.Panels;
import com.valaryrpg.core.gui.SlotSpec;
import com.valaryrpg.core.util.MmoItems;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * GUI vendita CommandPanels-like: deposit-slots liberi, resto da YAML, prezzi vanilla/MMOItems.
 */
public final class SellService {

    public static final String MENU = "sell";

    private final ValaryCorePlugin plugin;

    public SellService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!this.plugin.economy().available()) {
            this.plugin.messages().send(player, "sell.no-economy");
            return;
        }
        ConfigurationSection gui = gui();
        Inventory inventory = Panels.open(this.plugin, player, MENU, gui, Map.of());
        for (int slot : SlotSpec.expand(gui == null ? "0-44" : gui.getString("deposit-slots", "0-44"))) {
            if (slot >= 0 && slot < inventory.getSize() && !locked(slot)) {
                inventory.setItem(slot, null);
            }
        }
    }

    public boolean sellOnClose() {
        return this.plugin.configs().prices().getBoolean("settings.sell-on-close", false);
    }

    public boolean confirmSlot(int slot) {
        return "confirm".equals(GuiLayouts.action(GuiLayouts.button(gui(), slot)));
    }

    public boolean locked(int slot) {
        ConfigurationSection button = GuiLayouts.button(gui(), slot);
        String action = GuiLayouts.action(button);
        if (button != null && !action.equals("deposit") && !action.equals("none")) {
            return true;
        }
        return !SlotSpec.contains(gui() == null ? "0-44" : gui().getString("deposit-slots", "0-44"), slot);
    }

    public void click(Player player, int slot, Inventory inventory) {
        ConfigurationSection button = GuiLayouts.button(gui(), slot);
        if (button == null) {
            return;
        }
        if ("confirm".equals(GuiLayouts.action(button))) {
            sell(player, inventory, false);
        }
        PanelClicks.run(this.plugin, player, button);
    }

    public void sell(Player player, Inventory inventory, boolean closing) {
        if (!this.plugin.economy().available()) {
            refund(player, inventory);
            this.plugin.messages().send(player, "sell.no-economy");
            return;
        }
        double total = 0;
        int sold = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (locked(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Double price = priceOf(item);
            if (price == null) {
                player.getInventory().addItem(item.clone());
                inventory.setItem(slot, null);
                continue;
            }
            total += price * item.getAmount() * this.plugin.configs().prices().getDouble("settings.multiplier", 1.0D);
            sold += item.getAmount();
            inventory.setItem(slot, null);
        }
        if (sold <= 0) {
            if (!closing) {
                this.plugin.messages().send(player, "sell.empty");
            }
            return;
        }
        if (!this.plugin.economy().deposit(player, total)) {
            this.plugin.messages().send(player, "sell.failed");
            return;
        }
        this.plugin.messages().send(player, "sell.sold", Map.of(
                "amount", String.valueOf(sold),
                "money", String.format(Locale.US, "%.2f", total)
        ));
    }

    public void refund(Player player, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (locked(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item.clone());
            inventory.setItem(slot, null);
        }
    }

    private ConfigurationSection gui() {
        return this.plugin.configs().prices().getConfigurationSection("gui");
    }

    private Double priceOf(ItemStack item) {
        ConfigurationSection mmo = this.plugin.configs().prices().getConfigurationSection("mmoitems");
        if (mmo != null) {
            for (String key : mmo.getKeys(false)) {
                int split = key.indexOf('.');
                if (split <= 0) {
                    continue;
                }
                String type = key.substring(0, split);
                String id = key.substring(split + 1);
                if (MmoItems.matches(item, type, id)) {
                    return mmo.getDouble(key);
                }
            }
        }
        String material = item.getType().name();
        ConfigurationSection worth = this.plugin.configs().prices().getConfigurationSection("worth");
        if (worth == null || !worth.contains(material)) {
            return null;
        }
        return worth.getDouble(material);
    }

    public void sellAll(Player player) {
        if (!this.plugin.economy().available()) {
            this.plugin.messages().send(player, "sell.no-economy");
            return;
        }
        double total = 0;
        int sold = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Double price = priceOf(item);
            if (price == null) {
                continue;
            }
            total += price * item.getAmount() * this.plugin.configs().prices().getDouble("settings.multiplier", 1.0D);
            sold += item.getAmount();
            player.getInventory().setItem(slot, null);
        }
        if (sold <= 0) {
            this.plugin.messages().send(player, "sell.empty");
            return;
        }
        if (!this.plugin.economy().deposit(player, total)) {
            this.plugin.messages().send(player, "sell.failed");
            return;
        }
        this.plugin.messages().send(player, "sell.sold", Map.of(
                "amount", String.valueOf(sold),
                "money", String.format(Locale.US, "%.2f", total)
        ));
    }
}
