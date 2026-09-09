package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fly in RAM + SQL. Resta attivo dopo relog anche se al login sei in creative.
 */
public final class FlyService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();

    public FlyService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
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

    public void toggle(Player target, CommandSender initiator) {
        set(target, !wanted(target), initiator);
    }

    public void set(Player target, boolean enabled, CommandSender initiator) {
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
        player.setAllowFlight(enabled);
        if (!enabled && dropIfOff) {
            player.setFlying(false);
        } else if (enabled && this.plugin.configs().players().getBoolean("fly.start-flying", true)) {
            player.setFlying(true);
        }
    }
}
