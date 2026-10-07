package com.valaryrpg.core.module.expeditions;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.Module;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

/**
 * Spedizioni VIP: timer a timestamp nel DB (avanza anche offline), GUI con timer live,
 * GUI di riscatto animata e avviso quando la spedizione torna.
 */
public final class ExpeditionsModule implements Module {

    private final ValaryCorePlugin plugin;
    private ExpeditionService service;
    private ExpeditionMenu menu;
    private ExpeditionsListener listener;
    private BukkitTask refresh;
    private BukkitTask notifier;

    public ExpeditionsModule(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "expeditions";
    }

    @Override
    public void enable() {
        this.service = new ExpeditionService(this.plugin);
        this.menu = new ExpeditionMenu(this.service);
        this.listener = new ExpeditionsListener(this.service, this.menu);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.service.start();
        long period = Math.max(5L, this.service.config().getLong("settings.refresh-ticks", 20L));
        this.refresh = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::refreshOpen, period, period);
        long notifyPeriod = Math.max(20L, this.service.config().getLong("settings.notify-check-ticks", 100L));
        this.notifier = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this.service::notifyCompleted, notifyPeriod, notifyPeriod);
    }

    @Override
    public void disable() {
        if (this.refresh != null) {
            this.refresh.cancel();
            this.refresh = null;
        }
        if (this.notifier != null) {
            this.notifier.cancel();
            this.notifier = null;
        }
        if (this.service != null) {
            // consegna i riscatti aperti prima di chiudere le GUI (player ancora online)
            this.service.flushClaims();
        }
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
        if (this.menu != null) {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (this.menu.isMenu(top) || this.service.claimMenu().isMenu(top)) {
                    player.closeInventory();
                }
            }
        }
        if (this.service != null) {
            this.service.shutdown();
        }
    }

    @Override
    public void reload() {
        disable();
        enable();
    }

    public void open(Player player) {
        if (this.menu != null) {
            this.menu.open(player);
        }
    }

    public void finishNow(CommandSender sender, OfflinePlayer target) {
        if (this.service == null) {
            return;
        }
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        this.service.finishNow(target, done -> this.service.send(sender, done ? "admin-done" : "admin-none", Map.of("player", name)));
    }

    private void refreshOpen() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (this.menu.isMenu(top)) {
                this.menu.render(player, top);
            }
        }
    }
}
