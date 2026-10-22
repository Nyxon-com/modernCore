//made with love by nyxon

package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MultiHitService implements Listener {

  private static final long PRUNE_PERIOD_TICKS = 600L;
  private static final int STALE_TICKS = 200;

  private final ModernCorePlugin plugin;
  private final NamespacedKey windowKey;

  private final Map<UUID, Map<UUID, Integer>> lastHits = new HashMap<>();
  private BukkitTask pruneTask;

  public MultiHitService(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.windowKey = new NamespacedKey(plugin, "attacker_window");
  }

  public void start() {
    stop();
    this.pruneTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::prune, PRUNE_PERIOD_TICKS, PRUNE_PERIOD_TICKS);
  }

  public void stop() {
    if (this.pruneTask != null) {
      this.pruneTask.cancel();
      this.pruneTask = null;
    }
    this.lastHits.clear();
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onHit(EntityDamageByEntityEvent event) {
    LivingEntity victim = victim(event);
    Player attacker = victim == null ? null : attacker(event.getDamager());
    if (attacker == null) {
      return;
    }
    int window = prepare(victim);
    if (window <= 0) {
      return;
    }
    Map<UUID, Integer> perAttacker = this.lastHits.get(victim.getUniqueId());
    Integer last = perAttacker == null ? null : perAttacker.get(attacker.getUniqueId());
    if (last != null && Bukkit.getCurrentTick() - last < window) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHitApplied(EntityDamageByEntityEvent event) {
    LivingEntity victim = victim(event);
    Player attacker = victim == null ? null : attacker(event.getDamager());
    if (attacker == null) {
      return;
    }
    this.lastHits
        .computeIfAbsent(victim.getUniqueId(), ignored -> new HashMap<>())
        .put(attacker.getUniqueId(), Bukkit.getCurrentTick());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(EntityDeathEvent event) {
    this.lastHits.remove(event.getEntity().getUniqueId());
  }

  private int prepare(LivingEntity victim) {
    Integer stored = victim.getPersistentDataContainer().get(this.windowKey, PersistentDataType.INTEGER);
    int window;
    if (stored != null) {
      window = stored;
    } else {
      int override = yaml().getInt("multi-hit.attacker-window-ticks", -1);
      window = override >= 0 ? override : victim.getMaximumNoDamageTicks() / 2;
      victim.getPersistentDataContainer().set(this.windowKey, PersistentDataType.INTEGER, window);
    }
    if (victim.getMaximumNoDamageTicks() > 0) {
      victim.setMaximumNoDamageTicks(0);
      victim.setNoDamageTicks(0);
    }
    return window;
  }

  private LivingEntity victim(EntityDamageByEntityEvent event) {
    if (!yaml().getBoolean("multi-hit.enabled", true)) {
      return null;
    }
    if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player || living instanceof ArmorStand) {
      return null;
    }
    return living;
  }

  private static Player attacker(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  private void prune() {
    int now = Bukkit.getCurrentTick();
    Iterator<Map.Entry<UUID, Map<UUID, Integer>>> victims = this.lastHits.entrySet().iterator();
    while (victims.hasNext()) {
      Map<UUID, Integer> perAttacker = victims.next().getValue();
      perAttacker.values().removeIf(tick -> now - tick > STALE_TICKS);
      if (perAttacker.isEmpty()) {
        victims.remove();
      }
    }
  }

  private FileConfiguration yaml() {
    return this.plugin.configs().mechanics();
  }
}
