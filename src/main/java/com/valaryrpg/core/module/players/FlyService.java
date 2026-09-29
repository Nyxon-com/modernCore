package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fly in RAM + SQL. Resta attivo dopo relog e dopo respawn.
 */
public final class FlyService implements Listener {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    private BukkitTask guard;

    public FlyService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void start() {
        this.stop();
        this.guard = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                if (this.flightBlocked(player) && (player.getAllowFlight() || player.isFlying())) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (this.guard != null) {
            this.guard.cancel();
            this.guard = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!this.flightBlocked(player)) {
            return;
        }
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    public void restore(Player player, boolean enabled) {
        if (!this.plugin.configs().players().getBoolean("fly.persist", true)) {
            return;
        }
        apply(player, enabled, false);
    }

    public void restore(Player player) {
        if (!this.plugin.configs().players().getBoolean("fly.persist", true)) {
            return;
        }
        this.repository.find(player.getUniqueId()).ifPresent(profile -> restore(player, profile.fly()));
    }

    public void reapply(Player player) {
        apply(player, this.flying.contains(player.getUniqueId()), false);
    }

    public void restoreAfterRespawn(Player player) {
        if (!this.plugin.configs().players().getBoolean("fly.persist-death", true)) {
            return;
        }
        reapply(player);
        Tasks.nextTick(this.plugin, () -> reapply(player));
        Tasks.later(this.plugin, 2L, () -> reapply(player));
        Tasks.later(this.plugin, 10L, () -> reapply(player));
    }

    public void toggle(Player target, CommandSender initiator) {
        set(target, !wanted(target), initiator);
    }

    public void set(Player target, boolean enabled, CommandSender initiator) {
        if (enabled && this.flightBlocked(target)) {
            this.plugin.messages().send(target, "fly.blocked-world");
            boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            if (!self) {
                this.plugin.messages().send(initiator, "fly.blocked-world-other", Map.of("player", target.getName()));
            }
            this.apply(target, this.flying.contains(target.getUniqueId()), false);
            return;
        }
        apply(target, enabled, true);
        if (this.plugin.configs().players().getBoolean("fly.persist", true)) {
            Tasks.async(this.plugin, () -> this.repository.updateFly(target.getUniqueId(), target.getName(), enabled));
        }
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String stateKey = enabled ? "fly.enabled" : "fly.disabled";
        if (self) {
            this.plugin.messages().send(target, stateKey);
            return;
        }
        this.plugin.messages().send(initiator, stateKey + "-for", Map.of("player", target.getName()));
        this.plugin.messages().send(target, stateKey + "-by", Map.of("player", initiator.getName()));
    }

    public void unload(UUID uuid) {
        this.flying.remove(uuid);
    }

    private boolean wanted(Player player) {
        return this.flying.contains(player.getUniqueId()) || player.getAllowFlight();
    }

    private void apply(Player player, boolean enabled, boolean dropIfOff) {
        if (enabled) {
            this.flying.add(player.getUniqueId());
        } else {
            this.flying.remove(player.getUniqueId());
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean effective = enabled && !this.flightBlocked(player.getWorld());
        player.setAllowFlight(effective);
        if (!effective) {
            player.setFlying(false);
            if (dropIfOff) {
                player.setFallDistance(0.0F);
            }
        } else if (this.plugin.configs().players().getBoolean("fly.start-flying", true)) {
            player.setFlying(true);
        }
    }

    private boolean flightBlocked(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return this.flightBlocked(player.getWorld());
    }

    private boolean flightBlocked(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        List<String> allowed = this.plugin.configs().players().getStringList("fly.allowed-worlds");
        if (!allowed.isEmpty()) {
            for (String entry : allowed) {
                if (name.equals(entry.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        }
        for (String entry : this.plugin.configs().players().getStringList("fly.blocked-worlds")) {
            if (name.equals(entry.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String entry : this.plugin.configs().players().getStringList("fly.blocked-contains")) {
            String part = entry.toLowerCase(Locale.ROOT);
            if (!part.isBlank() && name.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
