package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Sounds;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LongJumpService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public LongJumpService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void tryJump(Player player, Material plate) {
        if (!this.plugin.configs().mechanics().getBoolean("long-jump.enabled", true)) {
            return;
        }
        ConfigurationSection plates = this.plugin.configs().mechanics().getConfigurationSection("long-jump.plates");
        if (plates == null) {
            return;
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
            if (!worlds.isEmpty() && (player.getWorld() == null || !worlds.contains(player.getWorld().getName()))) {
                continue;
            }
            launch(player, section);
            return;
        }
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

    public void unload(UUID uuid) {
        this.cooldown.remove(uuid);
    }
}
