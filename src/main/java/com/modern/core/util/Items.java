package com.modern.core.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Give in inventario e nome MiniMessage degli item, senza hardcode estetico.
 */
public final class Items {

    private Items() {
    }

    public static NamespacedKey noHologramKey(Plugin plugin) {
        return new NamespacedKey(plugin, "no_holo");
    }

    public static String miniName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "<gray>aria";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return MiniMessage.miniMessage().serialize(meta.displayName());
        }
        return "<lang:" + stack.getType().translationKey() + ">";
    }

    public static int leftoverAfterAdd(Player player, ItemStack stack, boolean dropOverflow, Plugin plugin) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
        int left = 0;
        for (ItemStack piece : leftover.values()) {
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            left += piece.getAmount();
            if (dropOverflow) {
                player.getWorld().dropItemNaturally(player.getLocation(), piece);
            }
        }
        return dropOverflow ? 0 : left;
    }
}
