package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LongJumpService {

    private final ModernCorePlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private BukkitTask lookTask;

    public LongJumpService(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!this.plugin.configs().mechanics().getBoolean("long-jump.enabled", true)
                || !this.plugin.configs().mechanics().getBoolean("long-jump.look-trigger", false)) {
            return;
        }
        int interval = Math.max(1, this.plugin.configs().mechanics().getInt("long-jump.look-interval-ticks", 4));
        this.lookTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickLooks, interval, interval);
    }

    public void stop() {
        if (this.lookTask != null) {
            this.lookTask.cancel();
            this.lookTask = null;
        }
    }

    public void tryJump(Player player, Material plate) {
        tryJump(player, plate, player.getLocation().getBlock().getLocation());
    }

    public void tryJump(Player player, Material plate, Location block) {
        if (!this.plugin.configs().mechanics().getBoolean("long-jump.enabled", true)) {
            return;
        }
        if (this.plugin.configs().mechanics().getBoolean("long-jump.look-required", false) && !lookingAt(player, block)) {
            return;
        }
        ConfigurationSection section = sectionOf(plate, block);
        if (section == null) {
            return;
        }
        launch(player, section);
    }

    public void tickLooks() {
        if (!this.plugin.configs().mechanics().getBoolean("long-jump.look-trigger", false)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Block target = lookingBlock(player);
            if (target == null) {
                continue;
            }
            tryJump(player, target.getType(), target.getLocation());
        }
    }

    public Block lookingBlock(Player player) {
        double range = this.plugin.configs().mechanics().getDouble("long-jump.look-range", 3.5D);
        RayTraceResult hit = player.rayTraceBlocks(range, FluidCollisionMode.NEVER);
        return hit == null ? null : hit.getHitBlock();
    }

    public void bindLooking(Player player, String preset) {
        Block target = lookingBlock(player);
        if (target == null) {
            this.plugin.messages().send(player, "long-jump.not-looking");
            return;
        }
        ConfigurationSection plates = this.plugin.configs().mechanics().getConfigurationSection("long-jump.plates." + preset);
        if (plates == null) {
            this.plugin.messages().send(player, "long-jump.unknown-preset", Map.of("preset", preset));
            return;
        }
        String key = locationKey(target.getLocation());
        String path = "long-jump.locations." + key;
        this.plugin.configs().mechanics().set(path + ".horizontal", plates.getDouble("horizontal", 2.0D));
        this.plugin.configs().mechanics().set(path + ".vertical", plates.getDouble("vertical", 0.8D));
        this.plugin.configs().mechanics().set(path + ".use-player-direction", plates.getBoolean("use-player-direction", true));
        this.plugin.configs().mechanics().set(path + ".material", target.getType().name());
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(player, "long-jump.bound", Map.of(
                "preset", preset,
                "block", key
        ));
    }

    public void unbindLooking(Player player) {
        Block target = lookingBlock(player);
        if (target == null) {
            this.plugin.messages().send(player, "long-jump.not-looking");
            return;
        }
        String key = locationKey(target.getLocation());
        this.plugin.configs().mechanics().set("long-jump.locations." + key, null);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(player, "long-jump.unbound", Map.of("block", key));
    }

    public void info(CommandSender sender, Player player) {
        Block target = lookingBlock(player);
        if (target == null) {
            this.plugin.messages().send(sender, "long-jump.not-looking");
            return;
        }
        this.plugin.messages().send(sender, "long-jump.info", Map.of(
                "block", locationKey(target.getLocation()),
                "material", target.getType().name()
        ));
    }

    public void unload(UUID uuid) {
        this.cooldown.remove(uuid);
    }

    private ConfigurationSection sectionOf(Material plate, Location block) {
        if (block != null && block.getWorld() != null) {
            ConfigurationSection location = this.plugin.configs().mechanics()
                    .getConfigurationSection("long-jump.locations." + locationKey(block));
            if (location != null) {
                return location;
            }
        }
        ConfigurationSection plates = this.plugin.configs().mechanics().getConfigurationSection("long-jump.plates");
        if (plates == null) {
            return null;
        }
        for (String key : plates.getKeys(false)) {
            ConfigurationSection section = plates.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", ""));
            if (material != plate) {
                continue;
            }
            List<String> worlds = section.getStringList("worlds");
            if (!worlds.isEmpty() && (block == null || block.getWorld() == null || !worlds.contains(block.getWorld().getName()))) {
                continue;
            }
            return section;
        }
        return null;
    }

    private void launch(Player player, ConfigurationSection section) {
        long now = System.currentTimeMillis();
        long wait = this.plugin.configs().mechanics().getLong("long-jump.cooldown-millis", 800L);
        Long last = this.cooldown.get(player.getUniqueId());
        if (last != null && now - last < wait) {
            return;
        }
        this.cooldown.put(player.getUniqueId(), now);
        double horizontal = section.getDouble("horizontal", 2.0D);
        double vertical = section.getDouble("vertical", 0.8D);
        Vector direction;
        if (section.getBoolean("use-player-direction", true)) {
            direction = player.getLocation().getDirection();
            direction.setY(0);
            if (direction.lengthSquared() < 0.01D) {
                direction = player.getLocation().getDirection();
            }
            direction.normalize().multiply(horizontal).setY(vertical);
        } else {
            direction = new Vector(
                    section.getDouble("velocity-x", 0.0D),
                    section.getDouble("velocity-y", vertical),
                    section.getDouble("velocity-z", horizontal)
            );
        }
        player.setVelocity(direction);
        Sounds.playRaw(
                this.plugin,
                player,
                this.plugin.configs().mechanics().getString("long-jump.sound", ""),
                "long-jump.sound"
        );
    }

    private boolean lookingAt(Player player, Location block) {
        Block target = lookingBlock(player);
        return target != null && block != null
                && target.getWorld() != null && block.getWorld() != null
                && target.getWorld().equals(block.getWorld())
                && target.getX() == block.getBlockX()
                && target.getY() == block.getBlockY()
                && target.getZ() == block.getBlockZ();
    }

    private static String locationKey(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
