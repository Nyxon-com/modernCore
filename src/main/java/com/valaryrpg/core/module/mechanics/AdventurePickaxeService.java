package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.util.MmoItems;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Solo i picconi MMOItems (tipo TOOL) scavano in adventure.
 */
public final class AdventurePickaxeService {

    public void stampInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            stamp(item);
        }
        stamp(inventory.getItemInOffHand());
    }

    public void stamp(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (!item.getType().name().endsWith("_PICKAXE")) {
            return;
        }
        if (!MmoItems.matches(item, "TOOL", null)) {
            return;
        }
        List<BlockType> types = new ArrayList<>();
        for (Material material : Tag.MINEABLE_PICKAXE.getValues()) {
            BlockType type = material.asBlockType();
            if (type != null) {
                types.add(type);
            }
        }
        types.add(BlockType.NOTE_BLOCK);
        item.setData(
                DataComponentTypes.CAN_BREAK,
                ItemAdventurePredicate.itemAdventurePredicate()
                        .addPredicate(BlockPredicate.predicate()
                                .blocks(RegistrySet.keySetFromValues(RegistryKey.BLOCK, types))
                                .build())
                        .build()
        );
    }
}
