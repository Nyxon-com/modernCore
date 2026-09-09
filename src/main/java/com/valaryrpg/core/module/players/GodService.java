package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * God mode in RAM + SQL. Blocca danno e fame.
 */
public final class GodService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final Set<UUID> gods = ConcurrentHashMap.newKeySet();

    public GodService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void restore(Player player, boolean enabled) {
        if (enabled) {
            this.gods.add(player.getUniqueId());
        }
    }

    public void restore(Player player) {
        restore(player, this.repository.god(player.getUniqueId()));
    }

    public boolean isGod(Player player) {
        return this.gods.contains(player.getUniqueId());
    }

    public void toggle(Player target, CommandSender initiator) {
        set(target, !isGod(target), initiator);
    }

    public void set(Player target, boolean enabled, CommandSender initiator) {
        if (enabled) {
            this.gods.add(target.getUniqueId());
            target.setFireTicks(0);
            target.setVisualFire(false);
        } else {
            this.gods.remove(target.getUniqueId());
        }
        Tasks.async(this.plugin, () -> this.repository.updateGod(target.getUniqueId(), target.getName(), enabled));
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String key = enabled ? "god.enabled" : "god.disabled";
        if (self) {
            this.plugin.messages().send(target, key);
            return;
        }
        this.plugin.messages().send(initiator, key + "-for", Map.of("player", target.getName()));
        this.plugin.messages().send(target, key + "-by", Map.of("player", initiator.getName()));
    }

    public void unload(UUID uuid) {
        this.gods.remove(uuid);
    }
}
