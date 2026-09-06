package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Durations;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModerationListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final ModerationService moderation;
    private final VanishService vanish;
    private final InvseeService invsee;

    public ModerationListener(
            ValaryCorePlugin plugin,
            ModerationService moderation,
            VanishService vanish,
            InvseeService invsee
    ) {
        this.plugin = plugin;
        this.moderation = moderation;
        this.vanish = vanish;
        this.invsee = invsee;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        this.moderation.loginBlock(event.getUniqueId(), event.getName(), event.getAddress().getHostAddress())
                .ifPresent(punishment -> event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        this.plugin.messages().component("moderation.ban-screen", Map.of(
                                "reason", this.plugin.messages().escape(punishment.reason()),
                                "staff", this.plugin.messages().escape(punishment.staff()),
                                "duration", Durations.formatRemaining(punishment.expiresAt())
                        ))
                ));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        this.moderation.activeMute(event.getPlayer()).ifPresent(mute -> {
            event.setCancelled(true);
            this.plugin.messages().send(event.getPlayer(), "moderation.mute-blocked", Map.of(
                    "reason", this.plugin.messages().escape(mute.reason()),
                    "duration", Durations.formatRemaining(mute.expiresAt())
            ));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (this.moderation.activeMute(event.getPlayer()).isEmpty()) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        List<String> blocked = this.plugin.configs().moderation().getStringList("mute.blocked-commands");
        if (!blocked.contains(label)) {
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "moderation.mute-command");
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
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeService.Holder holder)) {
            return;
        }
        if (InvseeService.filler(event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.invsee.syncFromGui(holder));
    }

    @EventHandler
    public void onInvseeDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeService.Holder holder)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(InvseeService::filler)) {
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
