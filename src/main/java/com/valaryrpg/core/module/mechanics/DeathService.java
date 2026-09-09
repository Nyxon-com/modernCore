package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.MenuHolder;
import com.valaryrpg.core.module.players.DeathSnapshot;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.util.ItemStacks;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Tasks;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DeathService {

    public static final String MENU = "checkdeath";

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository players;
    private final Map<UUID, DeathSnapshot> latest = new ConcurrentHashMap<>();

    public DeathService(ValaryCorePlugin plugin, PlayerDataRepository players) {
        this.plugin = plugin;
        this.players = players;
    }

    public void record(PlayerDeathEvent event) {
        Player player = event.getPlayer();
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
        String cause = player.getLastDamageCause() == null
                ? "UNKNOWN"
                : player.getLastDamageCause().getCause().name();
        String killer = event.getEntity().getKiller() == null
                ? ""
                : event.getEntity().getKiller().getName();
        String inventory = ItemStacks.encodePlayer(player.getInventory());
        DeathSnapshot snapshot = new DeathSnapshot(uuid, world, x, y, z, yaw, pitch, at, cause, killer, inventory);
        this.latest.put(uuid, snapshot);
        if (this.plugin.configs().players().getBoolean("back.include-death", true)) {
            this.plugin.lastLocations().remember(player);
        }
        applyDeathMessage(event, player, cause, killer);
        Tasks.async(this.plugin, () -> this.players.saveDeath(uuid, world, x, y, z, yaw, pitch, at, cause, killer, inventory));
    }

    public void show(CommandSender sender, String targetName) {
        Resolved resolved = resolve(sender, targetName);
        if (resolved == null) {
            return;
        }
        load(resolved, snapshot -> {
            if (snapshot == null) {
                this.plugin.messages().send(sender, "checkdeath.missing", Map.of("player", resolved.name));
                return;
            }
            Location location = snapshot.toLocation();
            String coords = format(snapshot);
            sender.sendMessage(this.plugin.messages().component("checkdeath.found", Map.of(
                    "player", resolved.name,
                    "world", snapshot.world(),
                    "coords", coords
            )).clickEvent(ClickEvent.copyToClipboard(coords)).hoverEvent(HoverEvent.showText(
                    this.plugin.messages().component("copycoord.hover", Map.of())
            )));
            this.plugin.messages().send(sender, "checkdeath.cause", Map.of(
                    "cause", snapshot.cause().isBlank() ? "-" : snapshot.cause(),
                    "killer", snapshot.killer().isBlank() ? "-" : snapshot.killer()
            ));
            ItemStack[] items = ItemStacks.decode(snapshot.inventory());
            this.plugin.messages().send(sender, "checkdeath.items", Map.of("count", String.valueOf(counted(items))));
            if (sender instanceof Player viewer) {
                openSnapshot(viewer, resolved.name, items);
                if (location != null && this.plugin.configs().mechanics().getBoolean("checkdeath.teleport", false)) {
                    viewer.teleport(location);
                }
            }
        });
    }

    public void restore(CommandSender sender, String targetName) {
        Resolved resolved = resolve(sender, targetName);
        if (resolved == null) {
            return;
        }
        Player online = Bukkit.getPlayer(resolved.uuid);
        if (online == null || !online.isOnline()) {
            this.plugin.messages().send(sender, "checkdeath.restore-offline", Map.of("player", resolved.name));
            return;
        }
        load(resolved, snapshot -> {
            Player target = Bukkit.getPlayer(resolved.uuid);
            if (target == null || !target.isOnline()) {
                this.plugin.messages().send(sender, "checkdeath.restore-offline", Map.of("player", resolved.name));
                return;
            }
            if (snapshot == null) {
                this.plugin.messages().send(sender, "checkdeath.missing", Map.of("player", resolved.name));
                return;
            }
            ItemStack[] items = ItemStacks.decode(snapshot.inventory());
            int count = counted(items);
            if (count == 0) {
                this.plugin.messages().send(sender, "checkdeath.restore-empty", Map.of("player", resolved.name));
                return;
            }
            if (this.plugin.configs().mechanics().getBoolean("checkdeath.restore-clear", true)) {
                ItemStacks.restorePlayer(target.getInventory(), items);
            } else {
                for (ItemStack item : items) {
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    target.getInventory().addItem(item.clone()).values()
                            .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                }
            }
            this.plugin.messages().send(sender, "checkdeath.restored", Map.of("player", resolved.name));
            if (sender != target) {
                this.plugin.messages().send(target, "checkdeath.restored-target", Map.of("player", sender.getName()));
            }
        });
    }

    public boolean isSnapshot(Inventory inventory) {
        return inventory.getHolder() instanceof MenuHolder holder && MENU.equals(holder.menu());
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

    private void applyDeathMessage(PlayerDeathEvent event, Player player, String cause, String killer) {
        if (!this.plugin.configs().mechanics().getBoolean("deaths.enabled", true)) {
            return;
        }
        if (this.plugin.configs().mechanics().getBoolean("deaths.hide-vanilla", true)) {
            event.deathMessage(null);
        }
        String path = "deaths.messages." + cause;
        String raw = this.plugin.configs().mechanics().getString(path, "");
        if (raw == null || raw.isBlank()) {
            raw = this.plugin.configs().mechanics().getString("deaths.messages.default", "");
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        event.deathMessage(this.plugin.messages().deserialize(raw
                .replace("{prefix}", this.plugin.configs().root().getString("plugin.prefix", ""))
                .replace("{player}", this.plugin.messages().escape(player.getName()))
                .replace("{killer}", this.plugin.messages().escape(killer == null || killer.isBlank() ? "-" : killer))
                .replace("{cause}", this.plugin.messages().escape(cause))
                .replace("{world}", player.getWorld() == null ? "" : player.getWorld().getName())
        ));
    }

    private void openSnapshot(Player viewer, String name, ItemStack[] items) {
        int rows = Math.min(6, Math.max(1, this.plugin.configs().mechanics().getInt("checkdeath.gui.rows", 6)));
        String titleRaw = this.plugin.configs().mechanics().getString("checkdeath.gui.title", "");
        net.kyori.adventure.text.Component title = titleRaw == null || titleRaw.isBlank()
                ? this.plugin.messages().component("checkdeath.gui-title", Map.of("player", name))
                : this.plugin.messages().deserialize(titleRaw.replace("{player}", name));
        MenuHolder holder = new MenuHolder(MENU, 1, name);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);
        for (int i = 0; i < items.length && i < inventory.getSize(); i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                inventory.setItem(i, items[i]);
            }
        }
        viewer.openInventory(inventory);
    }

    private void load(Resolved resolved, Consumer<DeathSnapshot> then) {
        DeathSnapshot ram = this.latest.get(resolved.uuid);
        if (ram != null) {
            then.accept(ram);
            return;
        }
        UUID uuid = resolved.uuid;
        Tasks.async(this.plugin, () -> {
            Optional<DeathSnapshot> found = this.players.lastDeath(uuid);
            Tasks.sync(this.plugin, () -> {
                found.ifPresent(snapshot -> this.latest.put(uuid, snapshot));
                then.accept(found.orElse(null));
            });
        });
    }

    private static int counted(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
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

    private String format(DeathSnapshot stored) {
        int decimals = this.plugin.configs().mechanics().getInt("copycoord.decimals", 1);
        return stored.world() + " "
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
