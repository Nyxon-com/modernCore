package com.modern.core.module.smp;



import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

public final class WorldSetup implements Listener {
  public static final String SPAWN = "spawn";
  public static final String OVERWORLD = "world";

  private static final Map<String, Double> BORDERS = Map.of(
      OVERWORLD, 45_000D,
      "world_nether", 11_400D,
      "world_the_end", 35_000D,
      SPAWN, 2_200D
  );

  private final SmpContext plugin;
  private Location hub;

  public WorldSetup(SmpContext plugin) {
    this.plugin = plugin;
  }

  public void start() {
    Bukkit.getScheduler().runTask(plugin.core(), this::prepare);
  }

  private void prepare() {
    World spawn = Bukkit.getWorld(SPAWN);
    if (spawn == null && new File(Bukkit.getWorldContainer(), SPAWN).isDirectory()) {
      spawn = new WorldCreator(SPAWN).createWorld();
    }
    if (spawn != null) {
      prepareSpawn(spawn);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + SPAWN + " normal");
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + SPAWN + " set gamemode adventure");
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + SPAWN + " set pvp false");
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + SPAWN + " set hunger false");
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + SPAWN + " set difficulty peaceful");
    }
    createIfMissing("world_nether", Environment.NETHER);
    createIfMissing("world_the_end", Environment.THE_END);
    for (World world : Bukkit.getWorlds()) {
      applyBorder(world);
    }
  }

  private void createIfMissing(String name, Environment environment) {
    if (Bukkit.getWorld(name) != null) {
      return;
    }
    new WorldCreator(name).environment(environment).createWorld();
  }

  private void prepareSpawn(World spawn) {
    int y = spawn.getHighestBlockYAt(0, 0);
    Location location = new Location(spawn, 0.5, y + 1.0, 0.5, 0f, 0f);
    spawn.setSpawnLocation(location);
    spawn.setPVP(false);
    spawn.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
    spawn.setGameRule(GameRules.ADVANCE_TIME, false);
    spawn.setGameRule(GameRules.ADVANCE_WEATHER, false);
    spawn.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
    spawn.setGameRule(GameRules.SPAWN_MOBS, false);
    spawn.setTime(6000);
    this.hub = location;
    writeCoreSpawn(location);
    plugin.getLogger().info("Spawn medieval pronto a " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
  }

  private void writeCoreSpawn(Location location) {
    File file = new File(plugin.getDataFolder().getParentFile(), "modernCore/modules/worlds.yml");
    if (!file.isFile()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    yaml.set("spawn.world", location.getWorld().getName());
    yaml.set("spawn.x", location.getX());
    yaml.set("spawn.y", location.getY());
    yaml.set("spawn.z", location.getZ());
    yaml.set("spawn.yaw", 0);
    yaml.set("spawn.pitch", 0);
    yaml.set("settings.default-world", SPAWN);
    yaml.set("settings.spawn-on-join", true);
    if (SPAWN.equals(yaml.getString("spawn.world"))
        && Math.abs(yaml.getDouble("spawn.y") - location.getY()) < 0.01
        && yaml.getBoolean("settings.spawn-on-join")) {
      return;
    }
    try {
      yaml.save(file);
    } catch (IOException ex) {
      plugin.getLogger().log(Level.WARNING, "Impossibile scrivere lo spawn di modernCore", ex);
    }
  }

  @EventHandler
  public void onWorld(WorldLoadEvent event) {
    applyBorder(event.getWorld());
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (hub == null || hub.getWorld() == null) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin.core(), () -> {
      if (player.isOnline()) {
        player.teleport(hub);
      }
    });
  }

  @EventHandler
  public void onPortal(PlayerPortalEvent event) {
    if (event.getFrom().getWorld() != null && SPAWN.equals(event.getFrom().getWorld().getName())) {
      event.setCancelled(true);
      SmpText.send(event.getPlayer(), "&7Per entrare nel mondo: &f/play");
    }
  }

  public void play(Player player) {
    if (plugin.combat().isTagged(player)) {
      SmpText.send(player, "&cNon puoi farlo in combattimento.");
      return;
    }
    World world = Bukkit.getWorld(OVERWORLD);
    if (world == null) {
      SmpText.send(player, "&cIl mondo di gioco non e caricato.");
      return;
    }
    Location spawn = world.getSpawnLocation();
    int y = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ());
    player.teleport(new Location(world, spawn.getBlockX() + 0.5, y + 1.0, spawn.getBlockZ() + 0.5));
    SmpText.send(player, "&aSei nel mondo. Il bordo e a &f22500 &ablocchi dal centro.");
  }

  private void applyBorder(World world) {
    Double diameter = BORDERS.get(world.getName());
    if (diameter == null) {
      return;
    }
    world.getWorldBorder().setCenter(0, 0);
    world.getWorldBorder().setSize(diameter);
    world.getWorldBorder().setWarningDistance(32);
  }
}
