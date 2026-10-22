//made with love by nyxon

package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Teleports implements Listener {

  private final ModernCorePlugin plugin;
  private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
  private final Map<UUID, Long> fallUntil = new ConcurrentHashMap<>();

  public Teleports(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void go(Player player, Location destination, int warmupSeconds, int cooldownSeconds, boolean cancelOnMove, Consumer<Player> after) {
    UUID uuid = player.getUniqueId();
    cancel(uuid);

    if (!Perms.has(this.plugin, player, "teleport-cooldown-bypass")) {
      Long until = this.cooldownUntil.get(uuid);
      long now = System.currentTimeMillis();
      if (until != null && until > now) {
        int left = (int) Math.ceil((until - now) / 1000.0D);
        this.plugin.messages().send(player, "teleport.cooldown", Map.of("seconds", String.valueOf(left)));
        showHud(player, "teleport.hud-cooldown", left);
        Sounds.play(this.plugin, player, "command-denied");
        return;
      }
    }

    if (warmupSeconds <= 0) {
      complete(player, destination, cooldownSeconds, after);
      return;
    }

    this.plugin.messages().send(player, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
    showHud(player, "teleport.hud-warmup", warmupSeconds);
    Sounds.play(this.plugin, player, "teleport-tick");

    Pending session = new Pending(destination.clone(), player.getLocation().clone(), cancelOnMove, warmupSeconds, cooldownSeconds, after, null);
    this.pending.put(uuid, session);
    int interval = Math.max(1, this.plugin.configs().root().getInt("teleport.hud.interval-ticks", 20));
    BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> tick(player), interval, interval);
    session.task = task;
  }

  public void cancel(UUID uuid) {
    Pending session = this.pending.remove(uuid);
    if (session != null && session.task != null) {
      session.task.cancel();
    }
  }

  public void safeLand(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    if (!this.plugin.configs().root().getBoolean("teleport.reset-fall", true)) {
      return;
    }
    player.setFallDistance(0.0F);
    player.setVelocity(new Vector(0, 0, 0));
    long grace = Math.max(0L, this.plugin.configs().root().getLong("teleport.fall-grace-millis", 1000L));
    this.fallUntil.put(player.getUniqueId(), System.currentTimeMillis() + grace);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    PlayerTeleportEvent.TeleportCause cause = event.getCause();
    if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN && cause != PlayerTeleportEvent.TeleportCause.COMMAND) {
      return;
    }
    Player player = event.getPlayer();
    safeLand(player);
    Tasks.nextTick(this.plugin, () -> safeLand(player));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onFall(EntityDamageEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
      return;
    }
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    Long until = this.fallUntil.get(player.getUniqueId());
    if (until == null || until < System.currentTimeMillis()) {
      return;
    }
    event.setCancelled(true);
    player.setFallDistance(0.0F);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    this.fallUntil.remove(event.getPlayer().getUniqueId());
  }

  private void tick(Player player) {
    Pending session = this.pending.get(player.getUniqueId());
    if (session == null || !player.isOnline()) {
      cancel(player.getUniqueId());
      return;
    }
    if (session.cancelOnMove && moved(session.origin, player.getLocation())) {
      cancel(player.getUniqueId());
      this.plugin.messages().send(player, "teleport.cancelled-move");
      Sounds.play(this.plugin, player, "teleport-cancelled");
      return;
    }
    session.remaining -= 1;
    if (session.remaining <= 0) {
      cancel(player.getUniqueId());
      complete(player, session.destination, session.cooldownSeconds, session.after);
      return;
    }
    showHud(player, "teleport.hud-warmup", session.remaining);
    Sounds.play(this.plugin, player, "teleport-tick");
  }

  private void complete(Player player, Location destination, int cooldownSeconds, Consumer<Player> after) {
    this.plugin.lastLocations().remember(player);
    safeLand(player);
    player.teleportAsync(destination).thenAccept(success -> {
      if (!Boolean.TRUE.equals(success) || !player.isOnline()) {
        return;
      }
      Tasks.sync(this.plugin, () -> {
        if (!player.isOnline()) {
          return;
        }
        safeLand(player);
        if (cooldownSeconds > 0 && !Perms.has(this.plugin, player, "teleport-cooldown-bypass")) {
          this.cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        Sounds.play(this.plugin, player, "teleport-success");
        if (after != null) {
          after.accept(player);
        }
      });
    });
  }

  private void showHud(Player player, String key, int seconds) {
    if (!this.plugin.configs().root().getBoolean("teleport.hud.enabled", true)) {
      return;
    }
    Map<String, String> placeholders = Map.of("seconds", String.valueOf(seconds));
    String type = this.plugin.configs().root().getString("teleport.hud.type", "ACTION_BAR");
    if (type != null && type.equalsIgnoreCase("TITLE")) {
      ConfigurationSection hud = this.plugin.configs().root().getConfigurationSection("teleport.hud");
      int fadeIn = hud == null ? 0 : hud.getInt("title-fade-in-ticks", 0);
      int stay = hud == null ? 25 : hud.getInt("title-stay-ticks", 25);
      int fadeOut = hud == null ? 5 : hud.getInt("title-fade-out-ticks", 5);
      player.showTitle(Title.title(
          this.plugin.messages().component(key, placeholders),
          net.kyori.adventure.text.Component.empty(),
          Title.Times.times(
              Duration.ofMillis(fadeIn * 50L),
              Duration.ofMillis(stay * 50L),
              Duration.ofMillis(fadeOut * 50L)
          )
      ));
      return;
    }
    player.sendActionBar(this.plugin.messages().component(key, placeholders));
  }

  public static boolean moved(Location from, Location to) {
    if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
      return true;
    }
    return from.toVector().distanceSquared(to.toVector()) > 0.04D;
  }

  private static final class Pending {
    private final Location destination;
    private final Location origin;
    private final boolean cancelOnMove;
    private int remaining;
    private final int cooldownSeconds;
    private final Consumer<Player> after;
    private BukkitTask task;

    private Pending(Location destination, Location origin, boolean cancelOnMove, int remaining, int cooldownSeconds, Consumer<Player> after, BukkitTask task) {
      this.destination = destination;
      this.origin = origin;
      this.cancelOnMove = cancelOnMove;
      this.remaining = remaining;
      this.cooldownSeconds = cooldownSeconds;
      this.after = after;
      this.task = task;
    }
  }
}
