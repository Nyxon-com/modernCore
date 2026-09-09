package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.worlds.WorldsModule;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.Perms;
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

    private final ValaryCorePlugin plugin;
    private final Map<String, Rule> rules = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotify = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRelocate = new ConcurrentHashMap<>();
    private BukkitTask task;

    public LimitYService(ValaryCorePlugin plugin) {
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
                ActionCommands.run(this.plugin, player, rule.commands, Map.of(
                        "min", String.valueOf(rule.min),
                        "max", String.valueOf(rule.max)
                ));
            }
            if (rule.teleport && ready(player.getUniqueId(), this.lastRelocate, now, actionCooldown)) {
                this.lastRelocate.put(player.getUniqueId(), now);
                relocate(player, world, rule, location);
            }
            if (ready(player.getUniqueId(), this.lastNotify, now, notifyCooldown)) {
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
        if (destination.getWorld() != null
                && destination.getWorld().equals(world)
                && destination.getY() >= rule.min
                && destination.getY() <= rule.max) {
            player.teleport(destination);
            return;
        }
        Location clamped = from.clone();
        if (from.getY() < rule.min) {
            clamped.setY(rule.min);
        } else {
            clamped.setY(rule.max);
        }
        player.teleport(clamped);
    }

    private static final Rule NONE = new Rule(0, 0, List.of(), false);

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
                section.getBoolean("teleport-spawn", commands.isEmpty())
        );
        this.rules.put(world.getName(), rule);
        return rule;
    }

    private static boolean ready(UUID uuid, Map<UUID, Long> last, long now, long cooldown) {
        Long previous = last.get(uuid);
        return previous == null || now - previous >= cooldown;
    }

    private record Rule(double min, double max, List<String> commands, boolean teleport) {
    }
}
