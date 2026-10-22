//made with love by nyxon

package com.modern.core.module.smp;

import com.modern.core.ModernCorePlugin;
import com.modern.core.config.YamlFile;
import com.modern.core.module.Module;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public final class SmpModule implements Module {
  private final ModernCorePlugin plugin;
  private SmpContext context;
  private final List<Listener> listeners = new ArrayList<>();
  private org.bukkit.scheduler.BukkitTask combatTask;
  private org.bukkit.scheduler.BukkitTask auctionTask;

  public SmpModule(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String id() {
    return "smp";
  }

  @Override
  public void enable() {
    YamlFile file = new YamlFile(this.plugin, "modules/smp.yml");
    file.load();
    FileConfiguration config = file.config();
    this.context = new SmpContext(this.plugin, config);
    CombatTagService combat = new CombatTagService(this.context);
    TeamStore teams = new TeamStore(this.context);
    AuctionService auctions = new AuctionService(this.context);
    WorldSetup worlds = new WorldSetup(this.context);
    this.context.bind(combat, teams, auctions, worlds);
    listen(new CombatTagListener(this.context, combat));
    listen(new TeamListener(this.context));
    listen(new AuctionListener(auctions));
    listen(new JoinKit(this.context));
    listen(worlds);
    worlds.start();
    this.combatTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, combat::tick, 20L, 20L);
    this.auctionTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, auctions::tickExpired, 20L * 60, 20L * 60);
  }

  @Override
  public void disable() {
    if (this.combatTask != null) {
      this.combatTask.cancel();
    }
    if (this.auctionTask != null) {
      this.auctionTask.cancel();
    }
    for (Listener listener : this.listeners) {
      HandlerList.unregisterAll(listener);
    }
    this.listeners.clear();
  }

  private void listen(Listener listener) {
    this.listeners.add(listener);
    this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
  }

  @Override
  public void reload() {
    disable();
    enable();
  }

  public SmpContext context() {
    return this.context;
  }
}
