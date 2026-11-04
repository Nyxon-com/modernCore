package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.MenuHolder;
import com.modern.core.util.Durations;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ModerationListener implements Listener {

  private final ModernCorePlugin plugin;
  private final ModerationService moderation;
  private final VanishService vanish;
  private final InvseeService invsee;
  private final BanHoldService holds;

  public ModerationListener(
      ModernCorePlugin plugin,
      ModerationService moderation,
      VanishService vanish,
      InvseeService invsee,
      BanHoldService holds
  ) {
    this.plugin = plugin;
    this.moderation = moderation;
    this.vanish = vanish;
    this.invsee = invsee;
    this.holds = holds;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    try {
      String ip = event.getAddress().getHostAddress();
      this.moderation.loginBlock(event.getUniqueId(), event.getName(), ip)
          .ifPresent(punishment -> {
            if (this.holds.shouldHold(punishment)) {
              this.holds.rememberIncoming(event.getUniqueId(), punishment);
              return;
            }
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                DisconnectScreens.of(this.plugin, punishment)
            );
          });
      if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
        this.moderation.prefetchMute(event.getUniqueId(), event.getName(), ip);
      }
    } catch (Exception exception) {
      this.plugin.getLogger().severe("Controllo ban fallito per " + event.getName() + ": " + exception.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onJoinHold(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    this.holds.onJoin(player);
    if (this.holds.holding(player.getUniqueId())) {
      event.joinMessage(null);
    }
  }

  @EventHandler
  public void onBanDialog(PlayerCustomClickEvent event) {
    if (!DisconnectScreens.leaveClick(this.plugin, event.getIdentifier())) {
      return;
    }
    if (event.getCommonConnection() instanceof PlayerGameConnection connection) {
      Player player = connection.getPlayer();
      if (this.holds.holding(player.getUniqueId())) {
        this.holds.leave(player);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    if (this.holds.holding(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
      return;
    }
    this.moderation.activeMute(event.getPlayer()).ifPresent(mute -> {
      event.setCancelled(true);
      this.plugin.messages().send(event.getPlayer(), "moderation.mute-blocked", Map.of(
          "reason", this.plugin.messages().escape(mute.reason()),
          "duration", Durations.formatRemaining(this.plugin, mute.expiresAt())
      ));
    });
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onCommand(PlayerCommandPreprocessEvent event) {
    if (this.holds.holding(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
      return;
    }
    if (this.moderation.activeMute(event.getPlayer()).isEmpty()) {
      return;
    }
    String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
    if (label.contains(":")) {
      label = label.substring(label.indexOf(':') + 1);
    }
    List<String> blocked = this.plugin.configs().moderation().getStringList("mute.blocked-commands");
    for (int i = 0; i < blocked.size(); i++) {
      if (label.equals(blocked.get(i).toLowerCase(Locale.ROOT))) {
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "moderation.mute-command");
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (!this.holds.holding(event.getPlayer().getUniqueId())) {
      return;
    }
    if (event.getFrom().getX() == event.getTo().getX()
        && event.getFrom().getY() == event.getTo().getY()
        && event.getFrom().getZ() == event.getTo().getZ()) {
      return;
    }
    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    if (this.holds.holding(uuid) && !this.holds.allowingTeleport(uuid)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (this.holds.holding(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent event) {
    if (this.holds.holding(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player player && this.holds.holding(player.getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onHunger(FoodLevelChangeEvent event) {
    if (event.getEntity() instanceof Player player && this.holds.holding(player.getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    this.vanish.restore(player);
    this.vanish.handleJoin(player);
    if (this.vanish.isVanished(player) && this.plugin.configs().moderation().getBoolean("vanish.silent-join", true)) {
      event.joinMessage(null);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    this.holds.forget(event.getPlayer().getUniqueId());
    if (this.vanish.isVanished(event.getPlayer()) && this.plugin.configs().moderation().getBoolean("vanish.silent-join", true)) {
      event.quitMessage(null);
    }
    this.vanish.unload(event.getPlayer().getUniqueId());
  }

  @EventHandler(ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player && this.vanish.noPickup(player)) {
      event.setCancelled(true);
    }
    if (event.getEntity() instanceof Player player && this.holds.holding(player.getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onVanishEditClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !VanishService.MENU.equals(holder.menu())) {
      return;
    }
    event.setCancelled(true);
    if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
      this.vanish.clickEdit(player, event.getSlot());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onInvseeClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player && this.holds.holding(player.getUniqueId())) {
      event.setCancelled(true);
      return;
    }
    if (!(event.getInventory().getHolder() instanceof InvseeService.Holder holder)) {
      return;
    }
    if (this.invsee.filler(event.getRawSlot())) {
      event.setCancelled(true);
      return;
    }
    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.invsee.syncFromGui(holder));
  }

  @EventHandler
  public void onInvseeDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player && this.holds.holding(player.getUniqueId())) {
      event.setCancelled(true);
      return;
    }
    if (!(event.getInventory().getHolder() instanceof InvseeService.Holder holder)) {
      return;
    }
    if (event.getRawSlots().stream().anyMatch(this.invsee::filler)) {
      event.setCancelled(true);
      return;
    }
    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.invsee.syncFromGui(holder));
  }

  @EventHandler
  public void onInvseeClose(InventoryCloseEvent event) {
    if (event.getInventory().getHolder() instanceof InvseeService.Holder holder) {
      this.invsee.syncFromGui(holder);
    }
  }
}
