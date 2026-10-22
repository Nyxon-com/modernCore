//made with love by nyxon

package com.modern.core.security;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpFullAccessService implements Listener {

  private final ModernCorePlugin plugin;
  private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

  public OpFullAccessService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    for (Player player : Bukkit.getOnlinePlayers()) {
      refresh(player);
    }
    Bukkit.getScheduler().runTaskTimer(this.plugin, this::refreshOnline, 100L, 100L);
  }

  public void stop() {
    for (UUID uuid : this.attachments.keySet()) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
        clearAttachment(player);
      }
    }
    this.attachments.clear();
  }

  public void refresh(Player player) {
    if (player == null) {
      return;
    }
    if (!player.isOp()) {
      clearAttachment(player);
      return;
    }
    PermissionAttachment attachment = this.attachments.computeIfAbsent(
        player.getUniqueId(),
        id -> player.addAttachment(this.plugin)
    );
    attachment.setPermission("*", true);
    player.recalculatePermissions();
  }

  private void refreshOnline() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.isOp()) {
        if (!this.attachments.containsKey(player.getUniqueId())) {
          refresh(player);
        }
      } else if (this.attachments.containsKey(player.getUniqueId())) {
        refresh(player);
      }
    }
  }

  private void clearAttachment(Player player) {
    PermissionAttachment attachment = this.attachments.remove(player.getUniqueId());
    if (attachment != null) {
      try {
        player.removeAttachment(attachment);
      } catch (IllegalArgumentException ignored) {

      }
      player.recalculatePermissions();
    }
  }

  private void afterOpCommand(String commandLine) {
    String command = stripNamespace(commandLine);
    String lower = command.toLowerCase(Locale.ROOT);
    boolean deop = lower.startsWith("deop ");
    boolean op = lower.startsWith("op ");
    if (!deop && !op) {
      return;
    }
    Bukkit.getScheduler().runTask(this.plugin, this::refreshOnline);
  }

  static String stripNamespace(String commandLine) {
    String command = commandLine == null ? "" : commandLine.trim();
    if (command.startsWith("/")) {
      command = command.substring(1).trim();
    }
    if (command.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
      command = command.substring("minecraft:".length()).trim();
    }
    return command;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(this.plugin, () -> refresh(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    this.attachments.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onServerCommand(ServerCommandEvent event) {
    afterOpCommand(event.getCommand());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
    afterOpCommand(event.getMessage());
  }
}
