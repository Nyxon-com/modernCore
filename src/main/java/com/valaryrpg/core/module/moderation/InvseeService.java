package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

public final class InvseeService {

    private final ValaryCorePlugin plugin;

    public InvseeService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Player target) {
        Holder holder = new Holder(target.getUniqueId());
        Component title = this.plugin.messages().component("invsee.title", Map.of("player", target.getName()));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inventory;
        fill(inventory, target);
        viewer.openInventory(inventory);
    }

    public void fill(Inventory inventory, Player target) {
        PlayerInventory playerInventory = target.getInventory();
        inventory.setItem(0, copy(playerInventory.getHelmet()));
        inventory.setItem(1, copy(playerInventory.getChestplate()));
        inventory.setItem(2, copy(playerInventory.getLeggings()));
        inventory.setItem(3, copy(playerInventory.getBoots()));
        inventory.setItem(4, copy(playerInventory.getItemInOffHand()));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int slot = 5; slot <= 8; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int slot = 0; slot < 36; slot++) {
            inventory.setItem(9 + slot, copy(playerInventory.getItem(slot)));
        }
    }

    public void syncFromGui(Holder holder) {
        Player target = Bukkit.getPlayer(holder.target);
        if (target == null || holder.inventory == null) {
            return;
        }
        Inventory gui = holder.inventory;
        PlayerInventory playerInventory = target.getInventory();
        playerInventory.setHelmet(airToNull(gui.getItem(0)));
        playerInventory.setChestplate(airToNull(gui.getItem(1)));
        playerInventory.setLeggings(airToNull(gui.getItem(2)));
        playerInventory.setBoots(airToNull(gui.getItem(3)));
        playerInventory.setItemInOffHand(airToNull(gui.getItem(4)));
        for (int slot = 0; slot < 36; slot++) {
            playerInventory.setItem(slot, airToNull(gui.getItem(9 + slot)));
        }
    }

    public static boolean filler(int slot) {
        return slot >= 5 && slot <= 8;
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
