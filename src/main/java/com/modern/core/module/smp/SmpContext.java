//made with love by nyxon

package com.modern.core.module.smp;

import com.modern.core.ModernCorePlugin;
import com.modern.core.economy.EconomyService;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class SmpContext {
  private final ModernCorePlugin core;
  private final FileConfiguration config;
  private CombatTagService combat;
  private TeamStore teams;
  private AuctionService auctions;
  private WorldSetup worlds;

  public SmpContext(ModernCorePlugin core, FileConfiguration config) {
    this.core = core;
    this.config = config;
  }

  public ModernCorePlugin core() {
    return this.core;
  }

  public FileConfiguration getConfig() {
    return this.config;
  }

  public Logger getLogger() {
    return this.core.getLogger();
  }

  public File getDataFolder() {
    return this.core.getDataFolder();
  }

  public Server getServer() {
    return this.core.getServer();
  }

  public EconomyService economy() {
    return this.core.economy();
  }

  public CombatTagService combat() {
    return this.combat;
  }

  public TeamStore teams() {
    return this.teams;
  }

  public AuctionService auctions() {
    return this.auctions;
  }

  public WorldSetup worlds() {
    return this.worlds;
  }

  void bind(CombatTagService combat, TeamStore teams, AuctionService auctions, WorldSetup worlds) {
    this.combat = combat;
    this.teams = teams;
    this.auctions = auctions;
    this.worlds = worlds;
  }
}
