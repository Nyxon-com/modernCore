package com.valaryrpg.core.security;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gli OP possono fare tutto: attachment * + LP user * true.
 */
public final class OpFullAccessService implements Listener {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lpGranted = new ConcurrentHashMap<>();

    public OpFullAccessService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::refreshAllOps, 100L, 100L);
    }

    public void stop() {
        for (UUID uuid : this.attachments.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clearAttachment(player);
            }
        }
        this.attachments.clear();
        this.lpGranted.clear();
    }

    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        if (!player.isOp()) {
            clearAttachment(player);
            // Deop: togli anche il * lasciato su LuckPerms (altrimenti resta full access).
            if (this.lpGranted.remove(player.getUniqueId()) != null
                    && Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "lp user " + player.getName() + " permission unset *"
                );
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "lp user " + player.getName() + " permission unset valary.command.see-all"
                );
            }
            return;
        }
        PermissionAttachment attachment = this.attachments.computeIfAbsent(
                player.getUniqueId(),
                id -> player.addAttachment(this.plugin)
        );
        attachment.setPermission("*", true);
        player.recalculatePermissions();
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null
                && this.lpGranted.put(player.getUniqueId(), Boolean.TRUE) == null) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission set * true"
            );
        }
    }

    private void refreshAllOps() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                if (!this.attachments.containsKey(player.getUniqueId())) {
                    refresh(player);
                }
            } else if (this.attachments.containsKey(player.getUniqueId())
                    || this.lpGranted.containsKey(player.getUniqueId())) {
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
                // già rimosso
            }
            player.recalculatePermissions();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.attachments.remove(event.getPlayer().getUniqueId());
        this.lpGranted.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase();
        if (command.startsWith("op ") || command.startsWith("deop ")) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.lpGranted.clear();
                refreshAllOps();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOp()) {
                        refresh(player);
                    }
                }
            });
        }
    }
}
