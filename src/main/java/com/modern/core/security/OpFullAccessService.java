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

  private static final String NAME_PATTERN = "[A-Za-z0-9_]{1,16}";

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
      revokePersisted(player.getUniqueId());
      return;
    }
    revokePersisted(player.getUniqueId());
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

  private void revokePersisted(UUID uuid) {
    if (uuid == null || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
      return;
    }
    dispatchUnset("lp user " + uuid + " permission unset *");
    dispatchUnset("lp user " + uuid + " permission unset valary.command.see-all");
  }

  private void revokePersisted(String name) {
    if (name == null || !name.matches(NAME_PATTERN) || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
      return;
    }
    dispatchUnset("lp user " + name + " permission unset *");
    dispatchUnset("lp user " + name + " permission unset valary.command.see-all");
  }

  private void dispatchUnset(String command) {
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
  }

  private void afterOpCommand(String commandLine) {
    String command = stripNamespace(commandLine);
    String lower = command.toLowerCase(Locale.ROOT);
    boolean deop = lower.startsWith("deop ");
    boolean op = lower.startsWith("op ");
    if (!deop && !op) {
      return;
    }
    if (deop) {
      revokePersisted(firstArg(command));
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

  private static String firstArg(String command) {
    int space = command.indexOf(' ');
    if (space < 0 || space == command.length() - 1) {
      return "";
    }
    String rest = command.substring(space + 1).trim();
    int next = rest.indexOf(' ');
    return next < 0 ? rest : rest.substring(0, next);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(this.plugin, () -> refresh(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    boolean tracked = this.attachments.remove(player.getUniqueId()) != null;
    if (tracked || player.isOp()) {
      revokePersisted(player.getUniqueId());
    }
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
