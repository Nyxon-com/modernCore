package com.modern.core.module.smp;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldSetup implements Listener {
  public static final String SPAWN = "spawn";
  public static final String OVERWORLD = "world";

  private static final Map<String, Double> BORDERS = Map.of(
      OVERWORLD, 45_000D,
      "world_nether", 11_400D,
      "world_the_end", 35_000D,
      SPAWN, 2_200D
  );

  private static final Set<Material> UNSAFE = Set.of(
      Material.LAVA, Material.WATER, Material.CACTUS, Material.MAGMA_BLOCK, Material.FIRE,
      Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
      Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.BEDROCK
  );

  private final SmpContext plugin;
  private final Map<UUID, Long> rtpCooldown = new ConcurrentHashMap<>();

  public WorldSetup(SmpContext plugin) {
    this.plugin = plugin;
  }

  public void start() {
    Bukkit.getScheduler().runTask(plugin.core(), () -> {
      for (World world : Bukkit.getWorlds()) {
        applyBorder(world);
      }
    });
  }

  @EventHandler
  public void onWorld(WorldLoadEvent event) {
    applyBorder(event.getWorld());
  }

  @EventHandler
  public void onPortal(PlayerPortalEvent event) {
    if (event.getFrom().getWorld() != null && SPAWN.equals(event.getFrom().getWorld().getName())) {
      event.setCancelled(true);
      SmpText.send(event.getPlayer(), "&7Per entrare nel mondo: &f/play &7o &f/rtp&7. Menu: &f/menu");
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

  public void rtp(Player player) {
    if (plugin.combat().isTagged(player)) {
      SmpText.send(player, "&cNon puoi farlo in combattimento.");
      return;
    }
    long now = System.currentTimeMillis();
    long cooldown = plugin.getConfig().getLong("rtp.cooldown-seconds", 45L) * 1000L;
    Long until = this.rtpCooldown.get(player.getUniqueId());
    if (until != null && until > now) {
      SmpText.send(player, "&cAspetta ancora &f" + ((until - now + 999) / 1000) + "s&c.");
      return;
    }
    String worldName = plugin.getConfig().getString("rtp.world", OVERWORLD);
    World world = Bukkit.getWorld(worldName == null ? OVERWORLD : worldName);
    if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
      SmpText.send(player, "&cIl mondo non e caricato.");
      return;
    }
    int attempts = Math.max(1, plugin.getConfig().getInt("rtp.attempts", 12));
    WorldBorder border = world.getWorldBorder();
    double half = Math.max(64D, (border.getSize() / 2D) - 48D);
    Location center = border.getCenter();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int attempt = 0; attempt < attempts; attempt++) {
      int x = (int) Math.floor(center.getX() + (random.nextDouble() * 2D - 1D) * half);
      int z = (int) Math.floor(center.getZ() + (random.nextDouble() * 2D - 1D) * half);
      int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      if (y <= world.getMinHeight() + 4) {
        continue;
      }
      Block ground = world.getBlockAt(x, y, z);
      if (!ground.getType().isSolid() || ground.isLiquid() || UNSAFE.contains(ground.getType())) {
        continue;
      }
      if (!world.getBlockAt(x, y + 1, z).isPassable() || !world.getBlockAt(x, y + 2, z).isPassable()) {
        continue;
      }
      player.teleport(new Location(world, x + 0.5, y + 1.0, z + 0.5));
      this.rtpCooldown.put(player.getUniqueId(), now + cooldown);
      SmpText.send(player, "&aTeletrasporto casuale: &f" + x + " " + (y + 1) + " " + z);
      return;
    }
    SmpText.send(player, "&cNessun punto sicuro. Riprova.");
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
