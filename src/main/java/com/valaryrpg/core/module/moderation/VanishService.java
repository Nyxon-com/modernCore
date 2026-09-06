package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VanishService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository players;
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> nightVision = new HashSet<>();
    private final Set<UUID> noPickup = new HashSet<>();

    public VanishService(ValaryCorePlugin plugin, PlayerDataRepository players) {
        this.plugin = plugin;
        this.players = players;
    }

    public void restore(Player player) {
        if (this.players.vanished(player.getUniqueId())) {
            apply(player, true, false);
        }
    }

    public boolean isVanished(Player player) {
        return this.vanished.contains(player.getUniqueId());
    }

    public boolean noPickup(Player player) {
        return this.noPickup.contains(player.getUniqueId());
    }

    public void toggle(Player target, CommandSender initiator) {
        set(target, !isVanished(target), initiator);
    }

    public void set(Player target, boolean enabled, CommandSender initiator) {
        apply(target, enabled, true);
        UUID uuid = target.getUniqueId();
        Tasks.async(this.plugin, () -> this.players.updateVanish(uuid, enabled));
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        this.plugin.messages().send(initiator, enabled ? "vanish.enabled-for" : "vanish.disabled-for", Map.of("player", target.getName()));
        if (!self) {
            this.plugin.messages().send(target, enabled ? "vanish.enabled-by" : "vanish.disabled-by", Map.of("player", initiator.getName()));
        }
    }

    public void handleJoin(Player joining) {
        for (UUID uuid : Set.copyOf(this.vanished)) {
            Player vanishedPlayer = Bukkit.getPlayer(uuid);
            if (vanishedPlayer == null || vanishedPlayer.equals(joining)) {
                continue;
            }
            if (!canSeeVanished(joining)) {
                joining.hidePlayer(this.plugin, vanishedPlayer);
            }
        }
        if (isVanished(joining)) {
            hideFromAll(joining);
        }
    }

    public boolean canSeeVanished(Player viewer) {
        return viewer.hasPermission(this.plugin.configs().root().getString("permissions.vanish-see", "valary.vanish.see"));
    }

    public void toggleNightVision(Player player) {
        if (this.nightVision.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            this.plugin.messages().send(player, "vanish.edit-off", Map.of("flag", "nightvision"));
            return;
        }
        this.nightVision.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        this.plugin.messages().send(player, "vanish.edit-on", Map.of("flag", "nightvision"));
    }

    public void toggleNoPickup(Player player) {
        if (!this.noPickup.add(player.getUniqueId())) {
            this.noPickup.remove(player.getUniqueId());
            this.plugin.messages().send(player, "vanish.edit-off", Map.of("flag", "pickup"));
            return;
        }
        this.plugin.messages().send(player, "vanish.edit-on", Map.of("flag", "pickup"));
    }

    public void unload(UUID uuid) {
        this.vanished.remove(uuid);
        this.nightVision.remove(uuid);
        this.noPickup.remove(uuid);
    }

    private void apply(Player target, boolean enabled, boolean announce) {
        if (enabled) {
            this.vanished.add(target.getUniqueId());
            hideFromAll(target);
            target.setInvisible(true);
            if (this.plugin.configs().moderation().getBoolean("vanish.night-vision", true)) {
                this.nightVision.add(target.getUniqueId());
                target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
            }
            if (this.plugin.configs().moderation().getBoolean("vanish.no-pickup", true)) {
                this.noPickup.add(target.getUniqueId());
            }
            if (announce && this.plugin.configs().moderation().getBoolean("vanish.fake-join-quit", false)) {
                Bukkit.broadcast(this.plugin.messages().component("join-quit.quit", Map.of(
                        "player", this.plugin.messages().escape(target.getName()),
                        "displayname", this.plugin.messages().escape(target.getName())
                )));
            }
        } else {
            this.vanished.remove(target.getUniqueId());
            this.nightVision.remove(target.getUniqueId());
            this.noPickup.remove(target.getUniqueId());
            target.setInvisible(false);
            target.removePotionEffect(PotionEffectType.NIGHT_VISION);
            showToAll(target);
            if (announce && this.plugin.configs().moderation().getBoolean("vanish.fake-join-quit", false)) {
                Bukkit.broadcast(this.plugin.messages().component("join-quit.join", Map.of(
                        "player", this.plugin.messages().escape(target.getName()),
                        "displayname", this.plugin.messages().escape(target.getName())
                )));
            }
        }
    }

    private void hideFromAll(Player vanishedPlayer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(vanishedPlayer) || canSeeVanished(other)) {
                continue;
            }
            other.hidePlayer(this.plugin, vanishedPlayer);
        }
    }

    private void showToAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(this.plugin, player);
        }
    }
}
