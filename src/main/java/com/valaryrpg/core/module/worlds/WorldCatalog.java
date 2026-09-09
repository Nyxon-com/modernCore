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
import java.util.Objects;

/**
 * Catalogo mondi: se Multiverse-Core è presente usa le sue config (niente doppio setup).
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

    public void syncFromMultiverseLater() {
        if (!multiverseEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, this::syncAll, 20L);
    }

    public boolean importWorld(World world) {
        FileConfiguration yaml = this.plugin.configs().worlds();
        String base = "worlds." + world.getName();
        Object mv = mvWorld(world);
        boolean overwrite = syncFromMultiverse();
        boolean mvSync = overwrite && mv != null;
        boolean changed = false;
        changed |= write(yaml, base + ".source", mv == null ? "bukkit" : "multiverse", overwrite);
        changed |= write(yaml, base + ".alias", string(mv, world.getName(), "getAliasOrName", "getAlias"), mvSync);
        changed |= write(yaml, base + ".environment", world.getEnvironment().name(), overwrite);
        changed |= write(yaml, base + ".pvp", bool(mv, world.getPVP(), "getPvp", "isPVPEnabled", "getPVP", "isPVP"), overwrite);
        changed |= write(yaml, base + ".difficulty", difficulty(mv, world), overwrite);
        changed |= write(yaml, base + ".gamemode", gamemodeName(mv), mvSync);
        changed |= write(yaml, base + ".force-gamemode", false, false);
        changed |= write(yaml, base + ".spawn-monsters", bool(mv, world.getAllowMonsters(), "canMonstersSpawn", "getMonsterSpawn"), overwrite);
        changed |= write(yaml, base + ".spawn-animals", bool(mv, world.getAllowAnimals(), "canAnimalsSpawn", "getAnimalSpawn"), overwrite);
        changed |= write(yaml, base + ".hunger", bool(mv, true, "isHunger", "getHunger", "isHungerEnabled"), mvSync);
        changed |= write(yaml, base + ".allow-flight", bool(mv, false, "isAllowFlight", "getAllowFlight"), mvSync);
        changed |= write(yaml, base + ".fall-damage", true, false);
        changed |= write(yaml, base + ".keep-spawn-in-memory", bool(mv, world.getKeepSpawnInMemory(), "isKeepSpawnInMemory", "getKeepSpawnInMemory"), overwrite);
        changed |= write(yaml, base + ".hidden", bool(mv, false, "isHidden"), mvSync);
        changed |= write(yaml, base + ".auto-heal", bool(mv, true, "getAutoHeal", "isAutoHeal"), mvSync);
        changed |= write(yaml, base + ".portal-forming", portalForming(mv), mvSync);
        changed |= write(yaml, base + ".weather", weather(mv), mvSync);
        changed |= write(
                yaml,
                base + ".generator",
                world.getGenerator() == null ? "default" : world.getGenerator().getClass().getName(),
                overwrite
        );
        changed |= write(yaml, base + ".seed", world.getSeed(), overwrite);
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
        if (multiverseOwns(world)) {
            return;
        }
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
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, yaml.getBoolean(base + ".auto-heal", true));
    }

    public boolean hunger(World world) {
        Object mv = liveMultiverse(world);
        if (mv != null) {
            return bool(mv, true, "isHunger", "getHunger", "isHungerEnabled");
        }
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".hunger", true);
    }

    public boolean allowFlight(World world) {
        Object mv = liveMultiverse(world);
        if (mv != null) {
            return bool(mv, false, "isAllowFlight", "getAllowFlight");
        }
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".allow-flight", false);
    }

    public boolean fallDamage(World world) {
        if (world == null) {
            return true;
        }
        return fallDamage(world.getName());
    }

    public boolean fallDamage(String worldName) {
        return this.plugin.configs().worlds().getBoolean("worlds." + worldName + ".fall-damage", true);
    }

    public void setFallDamage(String worldName, boolean enabled) {
        this.plugin.configs().worlds().set("worlds." + worldName + ".fall-damage", enabled);
        this.plugin.configs().saveWorlds();
    }

    public boolean forceGamemode(World world) {
        if (multiverseOwns(world)) {
            return false;
        }
        return this.plugin.configs().worlds().getBoolean("worlds." + world.getName() + ".force-gamemode", false);
    }

    public GameMode gamemode(World world) {
        Object mv = liveMultiverse(world);
        if (mv != null) {
            try {
                return GameMode.valueOf(gamemodeName(mv));
            } catch (IllegalArgumentException ignored) {
                return GameMode.SURVIVAL;
            }
        }
        try {
            return GameMode.valueOf(this.plugin.configs().worlds().getString("worlds." + world.getName() + ".gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    public String alias(World world) {
        Object mv = liveMultiverse(world);
        if (mv != null) {
            return string(mv, world.getName(), "getAliasOrName", "getAlias");
        }
        return this.plugin.configs().worlds().getString("worlds." + world.getName() + ".alias", world.getName());
    }

    private boolean multiverseOwns(World world) {
        return useMultiverseConfig() && mvWorld(world) != null;
    }

    private Object liveMultiverse(World world) {
        return useMultiverseConfig() ? mvWorld(world) : null;
    }

    private boolean useMultiverseConfig() {
        return this.plugin.configs().worlds().getBoolean("settings.use-multiverse-world-config", true)
                && multiverseEnabled();
    }

    private boolean syncFromMultiverse() {
        return this.plugin.configs().worlds().getBoolean("settings.sync-worlds-from-multiverse", true)
                && useMultiverseConfig();
    }

    private static boolean multiverseEnabled() {
        Plugin multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        return multiverse != null && multiverse.isEnabled();
    }

    private static boolean write(FileConfiguration yaml, String path, Object value, boolean overwrite) {
        if (!overwrite && yaml.contains(path)) {
            return false;
        }
        if (overwrite && yaml.contains(path) && Objects.equals(yaml.get(path), value)) {
            return false;
        }
        yaml.set(path, value);
        return true;
    }

    private static Object mvWorld(World world) {
        Object mv5 = mv5World(world);
        if (mv5 != null) {
            return mv5;
        }
        return mv4World(world);
    }

    private static Object mv5World(World world) {
        try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Object api = apiClass.getMethod("get").invoke(null);
            if (api == null) {
                return null;
            }
            Object manager = api.getClass().getMethod("getWorldManager").invoke(api);
            try {
                Object loaded = unwrap(manager.getClass().getMethod("getLoadedWorld", World.class).invoke(manager, world));
                if (loaded != null) {
                    return loaded;
                }
            } catch (NoSuchMethodException ignored) {
                // MV senza getLoadedWorld(World)
            }
            return unwrap(manager.getClass().getMethod("getWorld", World.class).invoke(manager, world));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object mv4World(World world) {
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

    private static Object unwrap(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method isEmpty = value.getClass().getMethod("isEmpty");
            if (Boolean.TRUE.equals(isEmpty.invoke(value))) {
                return null;
            }
            return value.getClass().getMethod("get").invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return value;
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

    private static String string(Object mv, String fallback, String... methods) {
        for (String method : methods) {
            Object value = invoke(mv, method);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return fallback;
    }

    private static String difficulty(Object mv, World world) {
        Object value = invoke(mv, "getDifficulty");
        if (value != null) {
            return String.valueOf(value);
        }
        return world.getDifficulty().name();
    }

    private static String gamemodeName(Object mv) {
        Object value = invoke(mv, "getGameMode");
        if (value instanceof GameMode mode) {
            return mode.name();
        }
        if (value != null) {
            return String.valueOf(value).toUpperCase(Locale.ROOT);
        }
        return GameMode.SURVIVAL.name();
    }

    private static boolean portalForming(Object mv) {
        Object value = invoke(mv, "getPortalForm");
        if (value == null) {
            return true;
        }
        String name = String.valueOf(value).toUpperCase(Locale.ROOT);
        return !name.contains("NONE") && !name.equals("FALSE");
    }

    private static String weather(Object mv) {
        if (mv == null) {
            return "UNSET";
        }
        if (!bool(mv, true, "isAllowWeather")) {
            return "CLEAR";
        }
        return "UNSET";
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
