package com.valaryrpg.core.module.worlds;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * All'avvio importa ogni mondo Bukkit/Multiverse in worlds.yml (solo chiavi mancanti) e applica le opzioni.
 */
public final class WorldCatalog {

    private final ValaryCorePlugin plugin;

    public WorldCatalog(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void syncAll() {
        boolean changed = false;
        if (this.plugin.configs().worlds().getBoolean("settings.import-on-start", true)) {
            for (World world : Bukkit.getWorlds()) {
                changed |= importWorld(world);
            }
            if (changed) {
                this.plugin.configs().saveWorlds();
                this.plugin.getLogger().info("Catalogo mondi aggiornato in worlds.yml.");
            }
        }
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    public boolean importWorld(World world) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        String base = "worlds." + world.getName();
        boolean changed = false;
        Object mv = mvWorld(world);
        changed |= put(yaml, base + ".source", mv == null ? "bukkit" : "multiverse");
        changed |= put(yaml, base + ".alias", string(mv, "getAlias", world.getName()));
        changed |= put(yaml, base + ".environment", world.getEnvironment().name());
        changed |= put(yaml, base + ".pvp", bool(mv, world.getPVP(), "isPVPEnabled", "getPVP", "isPVP"));
        changed |= put(yaml, base + ".difficulty", string(mv, "getDifficulty", world.getDifficulty().name()));
        changed |= put(yaml, base + ".gamemode", string(mv, "getGameMode", GameMode.SURVIVAL.name()));
        changed |= put(yaml, base + ".force-gamemode", false);
        changed |= put(yaml, base + ".spawn-monsters", bool(mv, world.getAllowMonsters(), "canMonstersSpawn", "getMonsterSpawn"));
        changed |= put(yaml, base + ".spawn-animals", bool(mv, world.getAllowAnimals(), "canAnimalsSpawn", "getAnimalSpawn"));
        changed |= put(yaml, base + ".hunger", bool(mv, true, "getHunger", "isHungerEnabled"));
        changed |= put(yaml, base + ".allow-flight", bool(mv, false, "getAllowFlight", "isAllowFlight"));
        changed |= put(yaml, base + ".keep-spawn-in-memory", bool(mv, world.getKeepSpawnInMemory(), "getKeepSpawnInMemory"));
        changed |= put(yaml, base + ".hidden", bool(mv, false, "isHidden"));
        changed |= put(yaml, base + ".auto-heal", true);
        changed |= put(yaml, base + ".portal-forming", true);
        changed |= put(yaml, base + ".weather", "UNSET");
        changed |= put(yaml, base + ".generator", world.getGenerator() == null ? "default" : world.getGenerator().getClass().getName());
        changed |= put(yaml, base + ".seed", world.getSeed());
        if (!yaml.contains("spawns." + world.getName())) {
            yaml.set("spawns." + world.getName() + ".x", world.getSpawnLocation().getX());
            yaml.set("spawns." + world.getName() + ".y", world.getSpawnLocation().getY());
            yaml.set("spawns." + world.getName() + ".z", world.getSpawnLocation().getZ());
            yaml.set("spawns." + world.getName() + ".yaw", world.getSpawnLocation().getYaw());
            yaml.set("spawns." + world.getName() + ".pitch", world.getSpawnLocation().getPitch());
            changed = true;
        }
        return changed;
    }

    public void apply(World world) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        String base = "worlds." + world.getName();
        if (!yaml.contains(base)) {
            return;
        }
        world.setPVP(yaml.getBoolean(base + ".pvp", world.getPVP()));
        try {
            world.setDifficulty(Difficulty.valueOf(yaml.getString(base + ".difficulty", world.getDifficulty().name()).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // lascia la difficulty attuale
        }
        world.setSpawnFlags(
                yaml.getBoolean(base + ".spawn-monsters", world.getAllowMonsters()),
                yaml.getBoolean(base + ".spawn-animals", world.getAllowAnimals())
        );
        world.setKeepSpawnInMemory(yaml.getBoolean(base + ".keep-spawn-in-memory", world.getKeepSpawnInMemory()));
        Boolean heal = yaml.getBoolean(base + ".auto-heal", true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, heal);
    }

    public boolean hunger(World world) {
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".hunger", true);
    }

    public boolean allowFlight(World world) {
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".allow-flight", false);
    }

    public boolean forceGamemode(World world) {
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".force-gamemode", false);
    }

    public GameMode gamemode(World world) {
        try {
            return GameMode.valueOf(this.plugin.configs().worlds().getString("worlds." + world.getName() + ".gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    public String alias(World world) {
        return this.plugin.configs().worlds().getString("worlds." + world.getName() + ".alias", world.getName());
    }

    private static boolean put(FileConfiguration yaml, String path, Object value) {
        if (yaml.contains(path)) {
            return false;
        }
        yaml.set(path, value);
        return true;
    }

    private static Object mvWorld(World world) {
        Plugin multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (multiverse == null || !multiverse.isEnabled()) {
            return null;
        }
        try {
            Method managerMethod = multiverse.getClass().getMethod("getMVWorldManager");
            Object manager = managerMethod.invoke(multiverse);
            try {
                return manager.getClass().getMethod("getMVWorld", World.class).invoke(manager, world);
            } catch (NoSuchMethodException ignored) {
                return manager.getClass().getMethod("getMVWorld", String.class).invoke(manager, world.getName());
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean bool(Object mv, boolean fallback, String... methods) {
        for (String method : methods) {
            Object value = invoke(mv, method);
            if (value instanceof Boolean flag) {
                return flag;
            }
        }
        return fallback;
    }

    private static String string(Object mv, String method, String fallback) {
        Object value = invoke(mv, method);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Object invoke(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
