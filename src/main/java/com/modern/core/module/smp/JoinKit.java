//made with love by nyxon

package com.modern.core.module.smp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class JoinKit implements Listener {
  private final SmpContext plugin;
  private final NamespacedKey key;

  public JoinKit(SmpContext plugin) {
    this.plugin = plugin;
    this.key = new NamespacedKey(plugin.core(), "joined");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    plugin.auctions().givePending(player);
    if (player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
      SmpText.send(player, "&7Bentornato. &f/play &7mondo, &f/shop &7negozio, &f/ah &7asta, &f/team &7team.");
      return;
    }
    player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    give(player, Material.STONE_SWORD, 1);
    give(player, Material.STONE_PICKAXE, 1);
    give(player, Material.STONE_AXE, 1);
    give(player, Material.BREAD, 16);
    give(player, Material.TORCH, 32);
    if (plugin.economy().available()) {
      plugin.economy().deposit(player, plugin.getConfig().getDouble("start-money", 100));
    }
    SmpText.send(player, "&eBenvenuto. Lo spawn e in adventure.");
    SmpText.send(player, "&7Per giocare: &f/play&7. Negozio: &f/shop&7. Asta: &f/ah sell <prezzo>&7.");
  }

  private void give(Player player, Material material, int amount) {
    for (ItemStack left : player.getInventory().addItem(new ItemStack(material, amount)).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), left);
    }
  }
}
