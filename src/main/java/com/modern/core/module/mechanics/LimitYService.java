package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.worlds.WorldsModule;
import com.modern.core.util.ActionCommands;
import com.modern.core.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite Y per mondo: comandi in cooldown, teleport solo se la destinazione è ancora nel range (altrimenti clamp).
 */
public final class LimitYService {

    private final ModernCorePlugin plugin;
    private final Map<String, Rule> rules = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotify = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRelocate = new ConcurrentHashMap<>();
    private BukkitTask task;

    public LimitYService(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        this.rules.clear();
        this.lastNotify.clear();
        this.lastAction.clear();
        this.lastRelocate.clear();
        if (!this.plugin.configs().mechanics().getBoolean("limity.enabled", true)) {
            return;
        }
        int interval = Math.max(1, this.plugin.configs().mechanics().getInt("limity.check-interval-ticks", 10));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, interval, interval);
    }

    public void initialize() {
        clearShippedExampleWorlds();
        start();
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public String describe(World world) {
        Rule rule = ruleOf(world);
        return rule == null ? "" : rule.min + " → " + rule.max;
    }

    public String commandsOf(World world) {
        Rule rule = ruleOf(world);
        if (rule == null || rule.commands.isEmpty()) {
            return "";
        }
        return String.join(" | ", rule.commands);
    }

    public void set(World world, double min, double max) {
        String path = "limity.worlds." + world.getName();
        this.plugin.configs().mechanics().set(path + ".min", min);
        this.plugin.configs().mechanics().set(path + ".max", max);
        this.plugin.configs().saveMechanics();
        this.rules.remove(world.getName());
    }

    /** Toglie una tantum i limiti di esempio world/world_nether copiati dal JAR. */
    private void clearShippedExampleWorlds() {
        var yaml = this.plugin.configs().mechanics();
        if (yaml.getBoolean("limity.example-worlds-cleared", false)) {
            return;
        }
        boolean removed = false;
        if (isShippedOverworldExample()) {
            yaml.set("limity.worlds.world", null);
            this.rules.remove("world");
            removed = true;
        }
        if (isShippedNetherExample()) {
            yaml.set("limity.worlds.world_nether", null);
            this.rules.remove("world_nether");
            removed = true;
        }
        yaml.set("limity.example-worlds-cleared", true);
        this.plugin.configs().saveMechanics();
        if (removed) {
            this.plugin.getLogger().info("LimitY: rimossi i limiti di esempio da world/world_nether. Resta solo ciò che imposti con /limity nel mondo in cui sei.");
        }
    }

    private boolean isShippedOverworldExample() {
        ConfigurationSection section = this.plugin.configs().mechanics().getConfigurationSection("limity.worlds.world");
        if (section == null) {
            return false;
        }
        List<String> commands = section.getStringList("commands");
        return section.getDouble("min") == -64
                && section.getDouble("max") == 320
                && commands.size() == 2
                && "[player] spawn".equals(commands.get(0))
                && commands.get(1).contains("Sei uscito dal limite Y");
    }

    private boolean isShippedNetherExample() {
        ConfigurationSection section = this.plugin.configs().mechanics().getConfigurationSection("limity.worlds.world_nether");
        if (section == null) {
            return false;
        }
        return section.getDouble("min") == 0
                && section.getDouble("max") == 127
                && section.getStringList("commands").isEmpty()
                && section.getBoolean("teleport-spawn", true);
    }

    public void remove(String worldName) {
        this.plugin.configs().mechanics().set("limity.worlds." + worldName, null);
        this.plugin.configs().saveMechanics();
        this.rules.remove(worldName);
        this.lastNotify.clear();
        this.lastAction.clear();
        this.lastRelocate.clear();
    }

    public void setNotify(World world, boolean enabled) {
        this.plugin.configs().mechanics().set("limity.worlds." + world.getName() + ".notify", enabled);
        this.plugin.configs().saveMechanics();
        this.rules.remove(world.getName());
    }

    public void setCommand(World world, String command) {
        String path = "limity.worlds." + world.getName();
        this.plugin.configs().mechanics().set(path + ".commands", List.of(command));
        this.plugin.configs().mechanics().set(path + ".teleport-spawn", false);
        this.plugin.configs().saveMechanics();
        this.rules.remove(world.getName());
    }

    public void clearCommands(World world) {
        String path = "limity.worlds." + world.getName();
        this.plugin.configs().mechanics().set(path + ".commands", List.of());
        this.plugin.configs().saveMechanics();
        this.rules.remove(world.getName());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long actionCooldown = this.plugin.configs().mechanics().getLong("limity.command-cooldown-millis", 3000L);
        long notifyCooldown = this.plugin.configs().mechanics().getLong("limity.notify-cooldown-millis", 3000L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Perms.has(this.plugin, player, "limity-bypass")) {
                continue;
            }
            World world = player.getWorld();
            Rule rule = ruleOf(world);
            if (rule == null) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getY() >= rule.min && location.getY() <= rule.max) {
                continue;
            }
            if (!rule.commands.isEmpty() && ready(player.getUniqueId(), this.lastAction, now, actionCooldown)) {
                this.lastAction.put(player.getUniqueId(), now);
                this.plugin.teleports().safeLand(player);
                ActionCommands.run(this.plugin, player, rule.commands, Map.of(
                        "min", String.valueOf(rule.min),
                        "max", String.valueOf(rule.max)
                ));
            }
            if (rule.teleport && ready(player.getUniqueId(), this.lastRelocate, now, actionCooldown)) {
                this.lastRelocate.put(player.getUniqueId(), now);
                relocate(player, world, rule, location);
            }
            if (rule.alerts && ready(player.getUniqueId(), this.lastNotify, now, notifyCooldown)) {
                this.lastNotify.put(player.getUniqueId(), now);
                this.plugin.messages().send(player, "limity.blocked", Map.of(
                        "min", String.valueOf(rule.min),
                        "max", String.valueOf(rule.max),
                        "world", world.getName()
                ));
            }
        }
    }

    private void relocate(Player player, World world, Rule rule, Location from) {
        Location destination = null;
        WorldsModule worlds = this.plugin.modules().get("worlds");
        if (worlds != null) {
            destination = worlds.spawns().locationOf(world);
        }
        if (destination == null) {
            destination = world.getSpawnLocation();
        }
        this.plugin.teleports().safeLand(player);
        if (destination.getWorld() != null
                && destination.getWorld().equals(world)
                && destination.getY() >= rule.min
                && destination.getY() <= rule.max) {
            player.teleport(destination);
            this.plugin.teleports().safeLand(player);
            return;
        }
        Location clamped = from.clone();
        if (from.getY() < rule.min) {
            clamped.setY(rule.min);
        } else {
            clamped.setY(rule.max);
        }
        player.teleport(clamped);
        this.plugin.teleports().safeLand(player);
    }

    private static final Rule NONE = new Rule(0, 0, List.of(), false, false);

    private Rule ruleOf(World world) {
        Rule cached = this.rules.get(world.getName());
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        ConfigurationSection section = this.plugin.configs().mechanics().getConfigurationSection("limity.worlds." + world.getName());
        if (section == null || !section.contains("min") || !section.contains("max")) {
            this.rules.put(world.getName(), NONE);
            return null;
        }
        List<String> commands = section.getStringList("commands");
        Rule rule = new Rule(
                section.getDouble("min"),
                section.getDouble("max"),
                List.copyOf(commands),
                section.getBoolean("teleport-spawn", commands.isEmpty()),
                section.getBoolean("notify", this.plugin.configs().mechanics().getBoolean("limity.notify", true))
        );
        this.rules.put(world.getName(), rule);
        return rule;
    }

    private static boolean ready(UUID uuid, Map<UUID, Long> last, long now, long cooldown) {
        Long previous = last.get(uuid);
        return previous == null || now - previous >= cooldown;
    }

    private record Rule(double min, double max, List<String> commands, boolean teleport, boolean alerts) {
    }
}
