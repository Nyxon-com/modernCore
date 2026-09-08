package com.valaryrpg.core.module.sell;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.GuiLayouts;
import com.valaryrpg.core.gui.PanelClicks;
import com.valaryrpg.core.gui.Panels;
import com.valaryrpg.core.gui.SlotSpec;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.MmoItems;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
        java.util.List<Runnable> rewards = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (locked(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Reward reward = rewardOf(item);
            if (reward == null) {
                player.getInventory().addItem(item.clone());
                inventory.setItem(slot, null);
                continue;
            }
            int amount = item.getAmount();
            total += reward.money * amount * this.plugin.configs().prices().getDouble("settings.multiplier", 1.0D);
            sold += amount;
            rewards.add(() -> applyReward(player, reward, amount));
            inventory.setItem(slot, null);
        }
        if (sold <= 0) {
            if (!closing) {
                this.plugin.messages().send(player, "sell.empty");
            }
            return;
        }
        if (total > 0 && !this.plugin.economy().deposit(player, total)) {
            this.plugin.messages().send(player, "sell.failed");
            return;
        }
        for (Runnable reward : rewards) {
            reward.run();
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

    private Reward rewardOf(ItemStack item) {
        ConfigurationSection custom = this.plugin.configs().prices().getConfigurationSection("custom");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                ConfigurationSection section = custom.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String type = section.getString("mmoitem-type", "");
                String id = section.getString("mmoitem-id", "");
                String materialName = section.getString("material", "");
                boolean mmo = type != null && !type.isBlank() && id != null && !id.isBlank();
                if (mmo && !MmoItems.matches(item, type, id)) {
                    continue;
                }
                if (!mmo) {
                    Material material = Material.matchMaterial(materialName);
                    if (material == null || item.getType() != material) {
                        continue;
                    }
                }
                return new Reward(
                        section.getDouble("money", 0.0D),
                        section.getInt("xp", 0),
                        section.getStringList("commands"),
                        section.getMapList("items")
                );
            }
        }
        Double price = priceOf(item);
        return price == null ? null : new Reward(price, 0, List.of(), List.of());
    }

    private void applyReward(Player player, Reward reward, int amount) {
        if (reward.xp > 0) {
            player.giveExp(reward.xp * amount);
        }
        ActionCommands.run(this.plugin, player, reward.commands, Map.of("amount", String.valueOf(amount)));
        for (Map<?, ?> raw : reward.items) {
            String type = raw.get("mmoitem-type") == null ? "" : String.valueOf(raw.get("mmoitem-type"));
            String id = raw.get("mmoitem-id") == null ? "" : String.valueOf(raw.get("mmoitem-id"));
            int give = Math.max(1, raw.get("amount") instanceof Number number ? number.intValue() : 1);
            ItemStack stack = MmoItems.stack(type, id, give);
            if (stack == null) {
                Material material = Material.matchMaterial(raw.get("material") == null ? "" : String.valueOf(raw.get("material")));
                if (material == null || material.isAir()) {
                    continue;
                }
                stack = new ItemStack(material, give);
            }
            player.getInventory().addItem(stack);
        }
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
        java.util.List<Runnable> rewards = new java.util.ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Reward reward = rewardOf(item);
            if (reward == null) {
                continue;
            }
            int amount = item.getAmount();
            total += reward.money * amount * this.plugin.configs().prices().getDouble("settings.multiplier", 1.0D);
            sold += amount;
            rewards.add(() -> applyReward(player, reward, amount));
            player.getInventory().setItem(slot, null);
        }
        if (sold <= 0) {
            this.plugin.messages().send(player, "sell.empty");
            return;
        }
        if (total > 0 && !this.plugin.economy().deposit(player, total)) {
            this.plugin.messages().send(player, "sell.failed");
            return;
        }
        for (Runnable reward : rewards) {
            reward.run();
        }
        this.plugin.messages().send(player, "sell.sold", Map.of(
                "amount", String.valueOf(sold),
                "money", String.format(Locale.US, "%.2f", total)
        ));
    }

    private record Reward(double money, int xp, List<String> commands, List<Map<?, ?>> items) {
    }
}
