package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Blocca l'ora del giorno per mondo (ciclo giorno/notte).
 */
public final class TimeService implements Listener {

    private final ValaryCorePlugin plugin;
    private BukkitTask task;

    public TimeService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        applyAll();
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::applyAll, 40L, 40L);
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        HandlerList.unregisterAll(this);
    }

    public void applyAll() {
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    public void apply(World world) {
        Long ticks = ticksOf(world.getName());
        if (ticks == null) {
            return;
        }
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(ticks);
    }

    public boolean isLocked(World world) {
        return ticksOf(world.getName()) != null;
    }

    public void lock(World world, long ticks) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        String path = "time." + world.getName();
        yaml.set(path + ".lock", true);
        yaml.set(path + ".ticks", ticks);
        this.plugin.configs().saveWorlds();
        apply(world);
    }

    public void unlock(World world) {
        this.plugin.configs().worlds().set("time." + world.getName(), null);
        this.plugin.configs().saveWorlds();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
    }

    public static long ticksOfPreset(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "day", "giorno", "sunrise" -> 1000L;
            case "noon", "mezzogiorno" -> 6000L;
            case "night", "notte" -> 13000L;
            case "midnight", "mezzanotte" -> 18000L;
            default -> {
                try {
                    yield Long.parseLong(raw);
                } catch (NumberFormatException exception) {
                    yield -1L;
                }
            }
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (isLocked(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    private Long ticksOf(String worldName) {
        ConfigurationSection section = this.plugin.configs().worlds().getConfigurationSection("time." + worldName);
        if (section == null || !section.getBoolean("lock", false)) {
            return null;
        }
        return section.getLong("ticks", 6000L);
    }
}
