package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.storage.StoredLocation;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Tasks;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeathService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository players;

    public DeathService(ValaryCorePlugin plugin, PlayerDataRepository players) {
        this.plugin = plugin;
        this.players = players;
    }

    public void record(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String world = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        long at = System.currentTimeMillis();
        if (this.plugin.configs().players().getBoolean("back.include-death", true)) {
            this.plugin.lastLocations().remember(player);
        }
        Tasks.async(this.plugin, () -> this.players.saveDeath(uuid, world, x, y, z, yaw, pitch, at));
    }

    public void show(CommandSender sender, String targetName) {
        Resolved resolved = resolve(sender, targetName);
        if (resolved == null) {
            return;
        }
        Optional<StoredLocation> death = this.players.lastDeath(resolved.uuid);
        if (death.isEmpty()) {
            this.plugin.messages().send(sender, "checkdeath.missing", Map.of("player", resolved.name));
            return;
        }
        StoredLocation stored = death.get();
        Location location = stored.toLocation();
        String coords = format(stored);
        sender.sendMessage(this.plugin.messages().component("checkdeath.found", Map.of(
                "player", resolved.name,
                "world", stored.worldName(),
                "coords", coords
        )).clickEvent(ClickEvent.copyToClipboard(coords)).hoverEvent(HoverEvent.showText(
                this.plugin.messages().component("copycoord.hover", Map.of())
        )));
        if (sender instanceof Player viewer && location != null
                && this.plugin.configs().mechanics().getBoolean("checkdeath.teleport", false)) {
            viewer.teleport(location);
        }
    }

    public void copy(CommandSender sender, String targetName) {
        Player target;
        if (targetName == null || targetName.isBlank()) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "copycoord.usage");
                return;
            }
            target = player;
        } else {
            target = OnlinePlayers.exact(targetName);
            if (target == null) {
                this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", targetName));
                return;
            }
        }
        Location location = target.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        int decimals = this.plugin.configs().mechanics().getInt("copycoord.decimals", 1);
        String coords = location.getWorld().getName() + " "
                + format(location.getX(), decimals) + " "
                + format(location.getY(), decimals) + " "
                + format(location.getZ(), decimals);
        sender.sendMessage(this.plugin.messages().component("copycoord.copied", Map.of(
                "player", target.getName(),
                "coords", coords
        )).clickEvent(ClickEvent.copyToClipboard(coords)).hoverEvent(HoverEvent.showText(
                this.plugin.messages().component("copycoord.hover", Map.of())
        )));
    }

    private Resolved resolve(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "checkdeath.usage");
                return null;
            }
            return new Resolved(player.getUniqueId(), player.getName());
        }
        Player online = OnlinePlayers.exact(targetName);
        if (online != null) {
            return new Resolved(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
        if (cached != null && cached.getName() != null) {
            return new Resolved(cached.getUniqueId(), cached.getName());
        }
        this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", targetName));
        return null;
    }

    private String format(StoredLocation stored) {
        int decimals = this.plugin.configs().mechanics().getInt("copycoord.decimals", 1);
        return stored.worldName() + " "
                + format(stored.x(), decimals) + " "
                + format(stored.y(), decimals) + " "
                + format(stored.z(), decimals);
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private record Resolved(UUID uuid, String name) {
    }
}
