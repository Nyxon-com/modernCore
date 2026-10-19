package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

/**
 * Heal e feed da valori YAML (nessun importo hardcoded).
 */
public final class VitalService {

    private final ModernCorePlugin plugin;

    public VitalService(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void heal(Player target, CommandSender initiator) {
        double health = this.plugin.configs().players().getDouble("heal.health", 20.0D);
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0D : max.getValue();
        target.setHealth(Math.min(Math.max(0.1D, health), cap));
        if (this.plugin.configs().players().getBoolean("heal.feed", true)) {
            feedStats(target);
        }
        target.setFireTicks(0);
        target.setVisualFire(false);
        if (this.plugin.configs().players().getBoolean("heal.clear-effects", false)) {
            for (PotionEffect effect : target.getActivePotionEffects()) {
                target.removePotionEffect(effect.getType());
            }
        }
        notify(target, initiator, "heal");
    }

    public void feed(Player target, CommandSender initiator) {
        feedStats(target);
        notify(target, initiator, "feed");
    }

    private void feedStats(Player target) {
        target.setFoodLevel(Math.min(20, Math.max(0, this.plugin.configs().players().getInt("feed.food", 20))));
        target.setSaturation((float) this.plugin.configs().players().getDouble("feed.saturation", 20.0D));
        target.setExhaustion(0);
    }

    private void notify(Player target, CommandSender initiator, String key) {
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        if (self) {
            this.plugin.messages().send(target, key + ".done");
            return;
        }
        this.plugin.messages().send(initiator, key + ".done-for", Map.of("player", target.getName()));
        this.plugin.messages().send(target, key + ".done-by", Map.of("player", initiator.getName()));
    }
}
