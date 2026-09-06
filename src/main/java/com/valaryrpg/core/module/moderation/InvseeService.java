package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.PanelItems;
import com.valaryrpg.core.gui.SlotSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

/**
 * Inventario altrui: layout (armor, offhand, filler, contents) da moderation.yml.
 */
public final class InvseeService {

    private final ValaryCorePlugin plugin;

    public InvseeService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Player target) {
        Holder holder = new Holder(target.getUniqueId());
        ConfigurationSection gui = gui();
        int rows = gui == null ? 6 : Math.min(6, Math.max(1, gui.getInt("rows", 6)));
        String titleRaw = gui == null ? "" : gui.getString("title", "");
        Component title = titleRaw == null || titleRaw.isBlank()
                ? this.plugin.messages().component("invsee.title", Map.of("player", target.getName()))
                : this.plugin.messages().deserialize(titleRaw.replace("{player}", target.getName()));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inventory;
        fill(inventory, target);
        viewer.openInventory(inventory);
    }

    public void fill(Inventory inventory, Player target) {
        PlayerInventory playerInventory = target.getInventory();
        inventory.setItem(slot("helmet", 0), copy(playerInventory.getHelmet()));
        inventory.setItem(slot("chestplate", 1), copy(playerInventory.getChestplate()));
        inventory.setItem(slot("leggings", 2), copy(playerInventory.getLeggings()));
        inventory.setItem(slot("boots", 3), copy(playerInventory.getBoots()));
        inventory.setItem(slot("offhand", 4), copy(playerInventory.getItemInOffHand()));
        ItemStack filler = PanelItems.from(this.plugin, fillerSection(), null);
        if (filler.getType().isAir()) {
            filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            filler.editMeta(meta -> meta.displayName(Component.empty()));
        }
        for (int slot : SlotSpec.expand(fillerSlots())) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler.clone());
            }
        }
        int start = slot("contents-start", 9);
        for (int index = 0; index < 36; index++) {
            int guiSlot = start + index;
            if (guiSlot >= 0 && guiSlot < inventory.getSize()) {
                inventory.setItem(guiSlot, copy(playerInventory.getItem(index)));
            }
        }
    }

    public void syncFromGui(Holder holder) {
        Player target = Bukkit.getPlayer(holder.target);
        if (target == null || holder.inventory == null) {
            return;
        }
        Inventory gui = holder.inventory;
        PlayerInventory playerInventory = target.getInventory();
        playerInventory.setHelmet(airToNull(gui.getItem(slot("helmet", 0))));
        playerInventory.setChestplate(airToNull(gui.getItem(slot("chestplate", 1))));
        playerInventory.setLeggings(airToNull(gui.getItem(slot("leggings", 2))));
        playerInventory.setBoots(airToNull(gui.getItem(slot("boots", 3))));
        playerInventory.setItemInOffHand(airToNull(gui.getItem(slot("offhand", 4))));
        int start = slot("contents-start", 9);
        for (int index = 0; index < 36; index++) {
            playerInventory.setItem(index, airToNull(gui.getItem(start + index)));
        }
    }

    public boolean filler(int slot) {
        return SlotSpec.contains(fillerSlots(), slot);
    }

    private int slot(String name, int fallback) {
        ConfigurationSection slots = gui() == null ? null : gui().getConfigurationSection("slots");
        return slots == null ? fallback : slots.getInt(name, fallback);
    }

    private String fillerSlots() {
        ConfigurationSection filler = fillerSection();
        return filler == null ? "5-8" : filler.getString("slots", "5-8");
    }

    private ConfigurationSection fillerSection() {
        return gui() == null ? null : gui().getConfigurationSection("filler");
    }

    private ConfigurationSection gui() {
        return this.plugin.configs().moderation().getConfigurationSection("invsee.gui");
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    private static ItemStack airToNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item;
    }

    public static final class Holder implements InventoryHolder {
        private final UUID target;
        private Inventory inventory;

        Holder(UUID target) {
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
