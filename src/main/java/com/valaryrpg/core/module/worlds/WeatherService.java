package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Blocco meteo per mondo. Multiverse non è richiesto: i mondi MV sono già in Bukkit.
 */
public final class WeatherService {

    public enum WeatherLock {
        CLEAR,
        RAIN,
        THUNDER;

        public static WeatherLock from(String raw) {
            if (raw == null) {
                return CLEAR;
            }
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "RAIN", "STORM", "DOWNFALL" -> RAIN;
                case "THUNDER", "THUNDERSTORM" -> THUNDER;
                default -> CLEAR;
            };
        }
    }

    private final ValaryCorePlugin plugin;

    public WeatherService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void applyAll() {
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    public void apply(World world) {
        WeatherLock lock = lockOf(world.getName());
        if (lock == null) {
            return;
        }
        applyType(world, lock);
    }

    public boolean isLocked(World world) {
        return lockOf(world.getName()) != null;
    }

    public void lock(World world, WeatherLock type) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        String path = "weather." + world.getName();
        yaml.set(path + ".lock", true);
        yaml.set(path + ".type", type.name());
        this.plugin.configs().saveWorlds();
        applyType(world, type);
    }

    public void unlock(World world) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        yaml.set("weather." + world.getName(), null);
        this.plugin.configs().saveWorlds();
    }

    private WeatherLock lockOf(String worldName) {
        ConfigurationSection section = this.plugin.configs().worlds().getConfigurationSection("weather." + worldName);
        if (section == null || !section.getBoolean("lock", false)) {
            return null;
        }
        return WeatherLock.from(section.getString("type", "CLEAR"));
    }

    private static void applyType(World world, WeatherLock type) {
        switch (type) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(Integer.MAX_VALUE);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setThunderDuration(Integer.MAX_VALUE);
            }
        }
    }
}
