package com.modern.core.module.smp;



import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class CombatTagListener implements Listener {
  private final SmpContext plugin;
  private final CombatTagService combat;

  public CombatTagListener(SmpContext plugin, CombatTagService combat) {
    this.plugin = plugin;
    this.combat = combat;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    Player attacker = attacker(event.getDamager());
    if (attacker == null || attacker.equals(victim)) {
      return;
    }
    if (!plugin.teams().allowPvp(attacker, victim)) {
      event.setCancelled(true);
      return;
    }
    combat.tag(attacker);
    combat.tag(victim);
  }

  @EventHandler(ignoreCancelled = true)
  public void onCommand(PlayerCommandPreprocessEvent event) {
    if (!combat.isTagged(event.getPlayer())) {
      return;
    }
    if (!combat.isBlocked(event.getMessage())) {
      return;
    }
    event.setCancelled(true);
    SmpText.send(event.getPlayer(), "&cNon puoi farlo mentre sei in combattimento.");
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (!combat.isTagged(player)) {
      return;
    }
    drop(player);
    combat.clear(player);
    for (Player online : plugin.getServer().getOnlinePlayers()) {
      SmpText.send(online, "&c" + player.getName() + " e uscito in combattimento.");
    }
  }

  private void drop(Player player) {
    dropAll(player, player.getInventory().getStorageContents());
    dropAll(player, player.getInventory().getArmorContents());
    dropAll(player, new ItemStack[] {player.getInventory().getItemInOffHand()});
    player.getInventory().clear();
  }

  private void dropAll(Player player, ItemStack[] contents) {
    if (contents == null) {
      return;
    }
    for (ItemStack stack : contents) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
    }
  }

  private Player attacker(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }
}
