//made with love by nyxon

package com.modern.core.module.vip;

import com.modern.core.ModernCorePlugin;
import com.modern.core.config.YamlFile;
import com.modern.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VipModule implements Module {

  private static final String EVENT_CLASS = "net.Indyuce.mmocore.api.event.PlayerExperienceGainEvent";

  private final ModernCorePlugin plugin;
  private final Listener listener = new Listener() {
  };
  private volatile Map<String, Double> ranks = Map.of();
  private volatile boolean enabled;
  private BukkitTask hookTask;
  private boolean hooked;

  public VipModule(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String id() {
    return "vip";
  }

  @Override
  public void enable() {
    YamlFile file = new YamlFile(this.plugin, "modules/vip.yml");
    file.load();
    this.enabled = file.config().getBoolean("xp-bonus.enabled", true);
    Map<String, Double> parsed = new LinkedHashMap<>();
    ConfigurationSection section = file.config().getConfigurationSection("xp-bonus.ranks");
    if (section != null) {
      for (String rank : section.getKeys(false)) {
        double value = section.getDouble(rank, 0);
        if (value > 0) {
          parsed.put(rank.toLowerCase(Locale.ROOT), value);
        }
      }
    }
    this.ranks = Map.copyOf(parsed);

    this.hookTask = Bukkit.getScheduler().runTask(this.plugin, this::hook);
  }

  @Override
  public void disable() {
    if (this.hookTask != null) {
      this.hookTask.cancel();
      this.hookTask = null;
    }
    HandlerList.unregisterAll(this.listener);
    this.hooked = false;
  }

  @Override
  public void reload() {
    disable();
    enable();
  }

  public double xpBonus(Player player) {
    return VipXpBonus.bestPercent(this.ranks, rank -> player.hasPermission("group." + rank));
  }

  @SuppressWarnings("unchecked")
  private void hook() {
    this.hookTask = null;
    if (this.hooked || !this.enabled || this.ranks.isEmpty()) {
      return;
    }
    Plugin mmocore = Bukkit.getPluginManager().getPlugin("MMOCore");
    if (mmocore == null || !mmocore.isEnabled()) {
      this.plugin.getLogger().warning("VIP: MMOCore assente, bonus XP disattivato.");
      return;
    }
    try {
      Class<?> raw = Class.forName(EVENT_CLASS, true, mmocore.getClass().getClassLoader());
      if (!Event.class.isAssignableFrom(raw)) {
        throw new IllegalStateException(EVENT_CLASS + " non è un Event");
      }
      Class<? extends Event> eventClass = (Class<? extends Event>) raw;
      Method getProfession = raw.getMethod("getProfession");
      Method getExperience = raw.getMethod("getExperience");
      Method setExperience = raw.getMethod("setExperience", double.class);
      Bukkit.getPluginManager().registerEvent(eventClass, this.listener, EventPriority.HIGH, (ignored, event) -> {
        if (!eventClass.isInstance(event) || !(event instanceof PlayerEvent playerEvent)) {
          return;
        }
        try {
          if (getProfession.invoke(event) != null) {
            return;
          }
          double percent = xpBonus(playerEvent.getPlayer());
          if (percent <= 0) {
            return;
          }
          double experience = (double) getExperience.invoke(event);
          double boosted = VipXpBonus.apply(experience, percent);
          if (boosted != experience) {
            setExperience.invoke(event, boosted);
          }
        } catch (ReflectiveOperationException exception) {
          this.plugin.getLogger().warning("VIP: bonus XP fallito: " + exception.getMessage());
        }
      }, this.plugin, true);
      this.hooked = true;
      this.plugin.getLogger().info("VIP: bonus XP classe agganciato a MMOCore (" + this.ranks.size() + " rank).");
    } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
      this.plugin.getLogger().warning("VIP: impossibile agganciare MMOCore: " + exception.getMessage());
    }
  }
}
