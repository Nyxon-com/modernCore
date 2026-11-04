package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.MmoItems;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdventurePickaxeService implements Listener {

  private final ModernCorePlugin plugin;
  private final Map<String, ItemAdventurePredicate> predicates = new HashMap<>();

  public AdventurePickaxeService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void stampInventory(Player player) {
    PlayerInventory inventory = player.getInventory();
    for (ItemStack item : inventory.getContents()) {
      stamp(item);
    }
    stamp(inventory.getItemInOffHand());
    stamp(player.getItemOnCursor());
  }

  public void stamp(ItemStack item) {
    if (item == null || item.getType().isAir() || !item.getType().name().endsWith("_PICKAXE")) {
      return;
    }
    if (canBreakHidden(item) && item.hasData(DataComponentTypes.CAN_BREAK)) {
      return;
    }
    List<Material> materials = MmoItems.canBreakBlocks(item);
    if (materials != null && !materials.isEmpty()) {
      item.setData(DataComponentTypes.CAN_BREAK, predicate(materials));
    } else if (!item.hasData(DataComponentTypes.CAN_BREAK)) {
      return;
    }
    hideCanBreak(item);
  }

  private void hideCanBreak(ItemStack item) {
    item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_DESTROYS));
    Set<DataComponentType> hidden = new HashSet<>();
    if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) {
      hidden.addAll(item.getData(DataComponentTypes.TOOLTIP_DISPLAY).hiddenComponents());
    }
    hidden.add(DataComponentTypes.CAN_BREAK);
    item.setData(
        DataComponentTypes.TOOLTIP_DISPLAY,
        TooltipDisplay.tooltipDisplay().hiddenComponents(hidden).build()
    );
  }

  private boolean canBreakHidden(ItemStack item) {
    if (!item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) {
      return false;
    }
    TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
    return display != null && display.hiddenComponents().contains(DataComponentTypes.CAN_BREAK);
  }

  private void stampView(Player player) {
    stampInventory(player);
    Inventory top = player.getOpenInventory().getTopInventory();
    if (top == null) {
      return;
    }
    for (int slot = 0; slot < top.getSize(); slot++) {
      ItemStack item = top.getItem(slot);
      if (item == null || item.getType().isAir() || !item.getType().name().endsWith("_PICKAXE") || canBreakHidden(item)) {
        continue;
      }
      stamp(item);
      top.setItem(slot, item);
    }
  }

  private ItemAdventurePredicate predicate(List<Material> materials) {
    String key = materials.stream().map(Material::name).sorted().collect(Collectors.joining(","));
    return this.predicates.computeIfAbsent(key, ignored -> {
      List<BlockType> types = new ArrayList<>();
      for (Material material : materials) {
        BlockType type = material.asBlockType();
        if (type != null) {
          types.add(type);
        }
      }
      return ItemAdventurePredicate.itemAdventurePredicate()
          .addPredicate(BlockPredicate.predicate()
              .blocks(RegistrySet.keySetFromValues(RegistryKey.BLOCK, types))
              .build())
          .build();
    });
  }

  private void later(Player player) {
    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
      if (player.isOnline()) {
        stampView(player);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    later(player);
    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
      if (player.isOnline()) {
        stampView(player);
      }
    }, 2L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    later(player);
    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
      if (player.isOnline()) {
        stampInventory(player);
      }
    }, 40L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHeld(PlayerItemHeldEvent event) {
    later(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      later(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      later(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onSwap(PlayerSwapHandItemsEvent event) {
    later(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player) {
      later(player);
    }
  }
}
