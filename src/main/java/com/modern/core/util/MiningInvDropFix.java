//made with love by nyxon

package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MiningInvDropFix implements Listener {

  private static final double BLOODSTONE_LINGOTTO_CHANCE = 0.40;
  private static final byte BLOODSTONE_NOTE = 5;

  private final ModernCorePlugin plugin;
  private Method getPlayer;
  private Method getDrops;
  private Method getBlock;

  public MiningInvDropFix(ModernCorePlugin plugin) {
    this.plugin = plugin;
    register();
  }

  private void register() {
    if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) {
      return;
    }
    try {
      Class<? extends Event> eventClass = Class
          .forName("net.Indyuce.mmocore.api.event.CustomBlockMineEvent")
          .asSubclass(Event.class);
      this.getPlayer = eventClass.getMethod("getPlayer");
      this.getDrops = eventClass.getMethod("getDrops");
      this.getBlock = eventClass.getMethod("getBlock");
      EventExecutor executor = (listener, event) -> onMine(event);
      Bukkit.getPluginManager().registerEvent(
          eventClass,
          this,
          EventPriority.HIGH,
          executor,
          this.plugin,
          true
      );
    } catch (ReflectiveOperationException ex) {
      this.plugin.getLogger().warning("MiningInvDropFix: evento MMOCore non trovato.");
    }
  }

  @SuppressWarnings("unchecked")
  private void onMine(Event event) {
    try {
      Object playerObj = this.getPlayer.invoke(event);
      if (!(playerObj instanceof Player player)) {
        return;
      }
      Object dropsObj = this.getDrops.invoke(event);
      if (!(dropsObj instanceof List<?> raw)) {
        return;
      }
      List<ItemStack> drops = (List<ItemStack>) raw;

      Object blockObj = this.getBlock.invoke(event);
      if (blockObj instanceof Block block && isBloodstone(block)) {
        drops.clear();
        if (ThreadLocalRandom.current().nextDouble() < BLOODSTONE_LINGOTTO_CHANCE) {
          MmoItems.give(player, "MATERIAL", "LINGOTTO_TERRESTRE", 1,
              "mi give MATERIAL LINGOTTO_TERRESTRE {player} 1 silent");
        }
        return;
      }

      if (drops.isEmpty()) {
        return;
      }
      List<ItemStack> copy = new ArrayList<>(drops);
      drops.clear();
      for (ItemStack stack : copy) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
          continue;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack left : leftover.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
      }
    } catch (ReflectiveOperationException ignored) {

    }
  }

  private static boolean isBloodstone(Block block) {
    if (block == null || block.getType() != Material.NOTE_BLOCK) {
      return false;
    }
    if (!(block.getBlockData() instanceof NoteBlock noteBlock)) {
      return false;
    }
    if (noteBlock.getInstrument() != Instrument.PIANO) {
      return false;
    }
    return noteBlock.getNote().getId() == BLOODSTONE_NOTE;
  }
}
