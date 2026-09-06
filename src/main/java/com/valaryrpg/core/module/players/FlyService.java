package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class FlyService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;

    public FlyService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void restore(Player player) {
        if (!this.plugin.configs().players().getBoolean("fly.persist", true)) {
            return;
        }
        this.repository.find(player.getUniqueId()).ifPresent(profile -> {
            if (profile.fly()) {
                apply(player, true, false);
            }
        });
    }

    public void toggle(Player target, CommandSender initiator) {
        set(target, !target.getAllowFlight(), initiator);
    }

    public void set(Player target, boolean enabled, CommandSender initiator) {
        apply(target, enabled, true);
        if (this.plugin.configs().players().getBoolean("fly.persist", true)) {
            this.repository.updateFly(target.getUniqueId(), enabled);
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

    private void apply(Player player, boolean enabled, boolean dropIfOff) {
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
