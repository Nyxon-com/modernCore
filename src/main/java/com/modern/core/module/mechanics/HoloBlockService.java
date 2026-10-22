//made with love by nyxon

package com.modern.core.module.mechanics;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.PanelItems;
import com.modern.core.util.ActionCommands;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoloBlockService {

  private final ModernCorePlugin plugin;
  private final Map<UUID, Location> selected = new ConcurrentHashMap<>();
  private final Map<UUID, Long> clickUntil = new ConcurrentHashMap<>();
  private final Map<String, Spawned> spawned = new ConcurrentHashMap<>();
  private final Set<String> spawning = ConcurrentHashMap.newKeySet();
  private BukkitTask task;
  private int ticks;

  public HoloBlockService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    stop();
    if (!enabled()) {
      return;
    }
    spawnLoaded();
    int interval = Math.max(1, yaml().getInt("holo-blocks.tick-ticks", 2));
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, interval, interval);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    this.ticks = 0;
    despawnAll();
    purgeTagged(null);
  }

  public void unload(UUID uuid) {
    this.selected.remove(uuid);
    this.clickUntil.remove(uuid);
  }

  public void giveWand(Player player) {
    ConfigurationSection wand = yaml().getConfigurationSection("holo-blocks.wand");
    ItemStack item = wand == null
        ? PanelItems.named("AMETHYST_SHARD", Material.AMETHYST_SHARD)
        : PanelItems.from(this.plugin, wand, player);
    item.editMeta(meta -> meta.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1));
    player.getInventory().addItem(item);
    this.plugin.messages().send(player, "holos.wand-given");
  }

  public boolean isWand(ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return false;
    }
    return item.getItemMeta().getPersistentDataContainer().has(wandKey(), PersistentDataType.BYTE);
  }

  public void handleWand(Player player, Block block) {
    mark(player, block.getLocation());
  }

  public void markLooking(Player player) {
    int range = Math.max(1, yaml().getInt("holo-blocks.select-range", 8));
    Block block = player.getTargetBlockExact(range);
    if (block == null) {
      this.plugin.messages().send(player, "holos.no-block");
      return;
    }
    mark(player, block.getLocation());
  }

  public void create(Player player, String name) {
    String id = normalize(name);
    if (id.isBlank()) {
      this.plugin.messages().send(player, "holos.invalid-name");
      return;
    }
    Location block = this.selected.get(player.getUniqueId());
    if (block == null || block.getWorld() == null) {
      this.plugin.messages().send(player, "holos.incomplete");
      return;
    }
    if (names().size() >= yaml().getInt("holo-blocks.max", 80)) {
      this.plugin.messages().send(player, "holos.max");
      return;
    }
    FileConfiguration config = yaml();
    String base = "holo-blocks.holos." + id;
    config.set(base, null);
    config.set(base + ".enabled", true);
    config.set(base + ".world", block.getWorld().getName());
    config.set(base + ".x", block.getBlockX());
    config.set(base + ".y", block.getBlockY());
    config.set(base + ".z", block.getBlockZ());
    List<String> lines = new ArrayList<>();
    for (String line : config.getStringList("holo-blocks.default-lines")) {
      lines.add(line.replace("{name}", id));
    }
    if (lines.isEmpty()) {
      lines.add("<gradient:#A78BFA:#EC4899><bold>" + id + "</bold></gradient>");
    }
    config.set(base + ".hologram.lines", lines);
    config.set(base + ".hologram.offset-y", config.getDouble("holo-blocks.default-offset-y", 1.35D));
    config.set(base + ".hologram.scale", config.getDouble("holo-blocks.default-scale", 1.0D));
    config.set(base + ".animation", config.getString("holo-blocks.default-animation", "bob"));
    config.set(base + ".particle", config.getString("holo-blocks.default-particle", "enchant"));
    config.set(base + ".click-particle", config.getString("holo-blocks.default-click-particle", "spark"));
    config.set(base + ".glow", config.getBoolean("holo-blocks.default-glow", false));
    config.set(base + ".permission", "");
    config.set(base + ".commands", List.of());
    config.set(base + ".sound", config.getString("holo-blocks.default-sound", "holo-click"));
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(player, "holos.created", Map.of("holo", id));
  }

  public void delete(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    despawn(id);
    purgeTagged(id);
    removeHoloFromYaml(id);
    this.plugin.configs().saveMechanics();
    this.plugin.messages().send(sender, "holos.deleted", Map.of("holo", id));
  }

  public void enable(CommandSender sender, String name, boolean on) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml().set("holo-blocks.holos." + id + ".enabled", on);
    this.plugin.configs().saveMechanics();
    if (on) {
      respawn(id);
    } else {
      despawn(id);
    }
    this.plugin.messages().send(sender, on ? "holos.enabled" : "holos.disabled", Map.of("holo", id));
  }

  public void list(CommandSender sender) {
    Set<String> names = names();
    if (names.isEmpty()) {
      this.plugin.messages().send(sender, "holos.list-empty");
      return;
    }
    List<String> lines = new ArrayList<>();
    for (String name : names) {
      boolean on = yaml().getBoolean("holo-blocks.holos." + name + ".enabled", true);
      lines.add(name + (on ? "" : " (off)"));
    }
    this.plugin.messages().send(sender, "holos.list", Map.of(
        "count", String.valueOf(lines.size()),
        "holos", String.join(", ", lines)
    ));
  }

  public void setCommand(CommandSender sender, String name, String command) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    String value = command == null ? "" : command.trim();
    if (value.isBlank()) {
      this.plugin.messages().send(sender, "holos.command-usage", Map.of("label", commandLabel()));
      return;
    }
    List<String> commands = new ArrayList<>(yaml().getStringList("holo-blocks.holos." + id + ".commands"));
    commands.add(value);
    yaml().set("holo-blocks.holos." + id + ".commands", commands);
    this.plugin.configs().saveMechanics();
    this.plugin.messages().send(sender, "holos.command-set", Map.of("holo", id, "command", value));
  }

  public void clearCommands(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml().set("holo-blocks.holos." + id + ".commands", List.of());
    this.plugin.configs().saveMechanics();
    this.plugin.messages().send(sender, "holos.commands-cleared", Map.of("holo", id));
  }

  public void addLine(CommandSender sender, String name, String text) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    if (text == null || text.isBlank()) {
      this.plugin.messages().send(sender, "holos.line-usage", Map.of("label", commandLabel()));
      return;
    }
    List<String> lines = new ArrayList<>(yaml().getStringList("holo-blocks.holos." + id + ".hologram.lines"));
    lines.add(text);
    yaml().set("holo-blocks.holos." + id + ".hologram.lines", lines);
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(sender, "holos.line-set", Map.of("holo", id));
  }

  public void clearLines(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml().set("holo-blocks.holos." + id + ".hologram.lines", List.of());
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(sender, "holos.lines-cleared", Map.of("holo", id));
  }

  public void setAnimation(CommandSender sender, String name, String animation) {
    setPreset(sender, name, "animation", "holo-blocks.animations", animation, "holos.animation-set", "holos.unknown-animation");
  }

  public void setParticle(CommandSender sender, String name, String particle) {
    setPreset(sender, name, "particle", "holo-blocks.particles", particle, "holos.particle-set", "holos.unknown-particle");
  }

  public void setClickParticle(CommandSender sender, String name, String particle) {
    setPreset(sender, name, "click-particle", "holo-blocks.click-particles", particle, "holos.click-particle-set", "holos.unknown-particle");
  }

  public void setSound(CommandSender sender, String name, String sound) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml().set("holo-blocks.holos." + id + ".sound", sound == null ? "" : sound.trim());
    this.plugin.configs().saveMechanics();
    this.plugin.messages().send(sender, "holos.sound-set", Map.of("holo", id, "sound", sound == null ? "" : sound));
  }

  public void setGlow(CommandSender sender, String name, String raw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    boolean on = raw != null && (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("si"));
    yaml().set("holo-blocks.holos." + id + ".glow", on);
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(sender, on ? "holos.glow-on" : "holos.glow-off", Map.of("holo", id));
  }

  public void setPermission(CommandSender sender, String name, String permission) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    String value = permission == null ? "" : permission.trim();
    yaml().set("holo-blocks.holos." + id + ".permission", value);
    this.plugin.configs().saveMechanics();
    this.plugin.messages().send(sender, "holos.permission-set", Map.of("holo", id, "permission", value.isBlank() ? "-" : value));
  }

  public void setScale(CommandSender sender, String name, String raw) {
    setNumber(sender, name, "hologram.scale", raw, "holos.scale-set");
  }

  public void setOffset(CommandSender sender, String name, String raw) {
    setNumber(sender, name, "hologram.offset-y", raw, "holos.offset-set");
  }

  public void setIcon(Player player, String name) {
    String id = require(player, name);
    if (id == null) {
      return;
    }
    ItemStack hand = player.getInventory().getItemInMainHand();
    String base = "holo-blocks.holos." + id + ".icon";
    if (hand == null || hand.getType().isAir()) {
      yaml().set(base, null);
      this.plugin.configs().saveMechanics();
      respawn(id);
      this.plugin.messages().send(player, "holos.icon-cleared", Map.of("holo", id));
      return;
    }
    yaml().set(base + ".material", hand.getType().name());
    yaml().set(base + ".scale", yaml().getDouble("holo-blocks.default-icon-scale", 0.55D));
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(player, "holos.icon-set", Map.of("holo", id));
  }

  public void info(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    ConfigurationSection holo = yaml().getConfigurationSection("holo-blocks.holos." + id);
    if (holo == null) {
      return;
    }
    this.plugin.messages().send(sender, "holos.info", Map.of(
        "holo", id,
        "world", holo.getString("world", "-"),
        "x", String.valueOf(holo.getInt("x")),
        "y", String.valueOf(holo.getInt("y")),
        "z", String.valueOf(holo.getInt("z")),
        "animation", holo.getString("animation", "none"),
        "particle", holo.getString("particle", "none"),
        "glow", holo.getBoolean("glow", false) ? "on" : "off",
        "commands", String.valueOf(holo.getStringList("commands").size())
    ));
  }

  public Set<String> names() {
    ConfigurationSection holos = yaml().getConfigurationSection("holo-blocks.holos");
    return holos == null ? Set.of() : holos.getKeys(false);
  }

  public Set<String> animationNames() {
    return keys("holo-blocks.animations");
  }

  public Set<String> particleNames() {
    return keys("holo-blocks.particles");
  }

  public Set<String> clickParticleNames() {
    return keys("holo-blocks.click-particles");
  }

  public boolean clickBlock(Player player, Block block) {
    if (block == null || player.isSneaking()) {
      return false;
    }
    return click(player, key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
  }

  public boolean clickEntity(Player player, UUID entityId) {
    if (player.isSneaking()) {
      return false;
    }
    for (Spawned spawned : this.spawned.values()) {
      if (entityId.equals(spawned.textId) || entityId.equals(spawned.iconId) || entityId.equals(spawned.hitId)) {
        return click(player, spawned.blockKey);
      }
    }
    return false;
  }

  public void onChunkLoad(World world, int chunkX, int chunkZ) {
    for (String id : names()) {
      ConfigurationSection holo = yaml().getConfigurationSection("holo-blocks.holos." + id);
      if (holo == null || !holo.getBoolean("enabled", true)) {
        continue;
      }
      if (!world.getName().equalsIgnoreCase(holo.getString("world", ""))) {
        continue;
      }
      int x = holo.getInt("x");
      int z = holo.getInt("z");
      if ((x >> 4) == chunkX && (z >> 4) == chunkZ) {
        spawn(id, holo);
      }
    }
  }

  public void onChunkUnload(World world, int chunkX, int chunkZ) {
    for (Spawned spawned : List.copyOf(this.spawned.values())) {
      Location block = spawned.block;
      if (block.getWorld() != null
          && block.getWorld().equals(world)
          && (block.getBlockX() >> 4) == chunkX
          && (block.getBlockZ() >> 4) == chunkZ) {
        despawn(spawned.id);
      }
    }
  }

  private boolean click(Player player, String blockKey) {
    Spawned spawned = null;
    for (Spawned candidate : this.spawned.values()) {
      if (candidate.blockKey.equals(blockKey)) {
        spawned = candidate;
        break;
      }
    }
    if (spawned == null) {
      return false;
    }
    ConfigurationSection holo = yaml().getConfigurationSection("holo-blocks.holos." + spawned.id);
    if (holo == null || !holo.getBoolean("enabled", true)) {
      return false;
    }
    String permission = holo.getString("permission", "");
    if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
      this.plugin.messages().send(player, "holos.no-permission", Map.of("holo", spawned.id));
      return true;
    }
    long now = System.currentTimeMillis();
    long wait = Math.max(0L, yaml().getLong("holo-blocks.click-cooldown-millis", 1500L));
    Long until = this.clickUntil.get(player.getUniqueId());
    if (until != null && until > now) {
      String seconds = String.format(java.util.Locale.ROOT, "%.1f", (until - now) / 1000.0D);
      this.plugin.messages().send(player, "holos.cooldown", Map.of("seconds", seconds, "holo", spawned.id));
      return true;
    }
    this.clickUntil.put(player.getUniqueId(), now + wait);
    burst(spawned.block.clone().add(0.5, 1.0, 0.5), holo.getString("click-particle", "spark"), "holo-blocks.click-particles");
    Sounds.play(this.plugin, player, holo.getString("sound", "holo-click"));
    List<String> commands = holo.getStringList("commands");
    String holoId = spawned.id;
    Tasks.later(this.plugin, 1L, () -> {
      if (!player.isOnline()) {
        return;
      }
      if (commands.isEmpty()) {
        this.plugin.messages().send(player, "holos.no-command", Map.of("holo", holoId));
        return;
      }

      ActionCommands.run(this.plugin, player, commands, Map.of("holo", holoId), true);
    });
    return true;
  }

  private void tick() {
    this.ticks++;
    long now = System.currentTimeMillis();
    double range = yaml().getDouble("holo-blocks.view-range", 24.0D);
    double rangeSq = range * range;
    int particleEvery = Math.max(1, yaml().getInt("holo-blocks.particle-every-ticks", 20));
    boolean particles = this.ticks % particleEvery == 0;
    for (Spawned spawned : this.spawned.values()) {
      Location center = spawned.block.clone().add(0.5, spawned.offsetY, 0.5);
      if (center.getWorld() == null || !nearby(center, rangeSq)) {
        continue;
      }

      if (spawned.animation != null && !spawned.animation.equalsIgnoreCase("none")) {
        animate(spawned, now);
      }
      if (particles) {
        burst(center, spawned.particle, "holo-blocks.particles");
      }
    }
  }

  private void animate(Spawned spawned, long now) {
    var text = entity(spawned.textId, TextDisplay.class);
    var icon = entity(spawned.iconId, ItemDisplay.class);
    if (text == null && icon == null) {
      return;
    }
    ConfigurationSection preset = yaml().getConfigurationSection("holo-blocks.animations." + spawned.animation);
    float offsetX = 0f;
    float offsetY = spawned.offsetY;
    float offsetZ = 0f;
    float scale = spawned.scale;
    Quaternionf rotation = new Quaternionf();
    if (preset != null) {
      double t = now / 50.0D;
      String type = spawned.animation.toLowerCase(Locale.ROOT);
      if (type.equals("bob")) {
        offsetY += (float) (Math.sin(t * preset.getDouble("speed", 0.08D)) * preset.getDouble("height", 0.12D));
      } else if (type.equals("spin")) {
        rotation.rotateY((float) Math.toRadians((t * preset.getDouble("speed", 6.0D)) % 360.0D));
      } else if (type.equals("pulse")) {
        double min = preset.getDouble("min-scale", 0.92D);
        double max = preset.getDouble("max-scale", 1.08D);
        double wave = (Math.sin(t * preset.getDouble("speed", 0.12D)) + 1.0D) / 2.0D;
        scale *= (float) (min + (max - min) * wave);
      } else if (type.equals("wave")) {
        double speed = preset.getDouble("speed", 0.08D);
        offsetX = (float) (Math.sin(t * speed) * preset.getDouble("width", 0.18D));
        offsetY += (float) (Math.cos(t * speed) * preset.getDouble("height", 0.10D));
      }
    }
    int duration = Math.max(1, yaml().getInt("holo-blocks.tick-ticks", 10));
    apply(text, offsetX, offsetY, offsetZ, scale, rotation, duration);
    apply(icon, offsetX, offsetY + spawned.iconOffsetY, offsetZ, spawned.iconScale, rotation, duration);
  }

  private void burst(Location at, String presetName, String root) {
    if (presetName == null || presetName.isBlank() || presetName.equalsIgnoreCase("none")) {
      return;
    }
    ConfigurationSection preset = yaml().getConfigurationSection(root + "." + presetName.toLowerCase(Locale.ROOT));
    if (preset == null) {
      return;
    }
    String typeName = preset.getString("type", "");
    if (typeName == null || typeName.isBlank()) {
      return;
    }
    Particle particle;
    try {
      particle = Particle.valueOf(typeName.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return;
    }
    double offset = preset.getDouble("offset", 0.25D);
    at.getWorld().spawnParticle(
        particle,
        at,
        Math.max(1, preset.getInt("count", 4)),
        offset,
        offset,
        offset,
        preset.getDouble("extra", 0.0D)
    );
  }

  private void spawnLoaded() {
    for (String id : names()) {
      ConfigurationSection holo = yaml().getConfigurationSection("holo-blocks.holos." + id);
      if (holo == null || !holo.getBoolean("enabled", true)) {
        continue;
      }
      spawn(id, holo);
    }
  }

  private void respawn(String id) {
    despawn(id);
    ConfigurationSection holo = yaml().getConfigurationSection("holo-blocks.holos." + id);
    if (holo != null && holo.getBoolean("enabled", true)) {
      spawn(id, holo);
    }
  }

  private void spawn(String id, ConfigurationSection holo) {
    if (!enabled() || !this.spawning.add(id)) {
      return;
    }
    try {
      spawnNow(id, holo);
    } finally {
      this.spawning.remove(id);
    }
  }

  private void spawnNow(String id, ConfigurationSection holo) {
    World world = Bukkit.getWorld(holo.getString("world", ""));
    if (world == null) {
      return;
    }
    Location block = new Location(world, blockCoord(holo, "x"), blockCoord(holo, "y"), blockCoord(holo, "z"));
    despawn(id);
    world.getChunkAt(block.getBlockX() >> 4, block.getBlockZ() >> 4);
    purgeNearby(block, id);
    ConfigurationSection hologram = holo.getConfigurationSection("hologram");
    float offsetY = (float) (hologram == null ? 1.35D : hologram.getDouble("offset-y", 1.35D));
    float scale = (float) (hologram == null ? 1.0D : hologram.getDouble("scale", 1.0D));
    Location anchor = block.clone().add(0.5, 0, 0.5);
    boolean glow = holo.getBoolean("glow", false);
    String animation = holo.getString("animation", "none");
    Component text = linesOf(hologram);
    TextDisplay display = world.spawn(anchor, TextDisplay.class, entity -> {
      entity.text(text);
      style(entity, hologram, scale, animation, glow);
      tag(entity, id);
    });
    UUID iconId = null;
    float iconScale = (float) yaml().getDouble("holo-blocks.default-icon-scale", 0.55D);
    float iconOffsetY = (float) yaml().getDouble("holo-blocks.default-icon-offset-y", -0.45D);
    ConfigurationSection icon = holo.getConfigurationSection("icon");
    if (icon != null) {
      Material material = Material.matchMaterial(icon.getString("material", ""));
      if (material != null && !material.isAir()) {
        iconScale = (float) icon.getDouble("scale", iconScale);
        iconOffsetY = (float) icon.getDouble("offset-y", iconOffsetY);
        float finalScale = iconScale;
        float finalOffset = offsetY + iconOffsetY;
        boolean spin = "spin".equalsIgnoreCase(animation);
        ItemDisplay item = world.spawn(anchor, ItemDisplay.class, entity -> {
          entity.setItemStack(new ItemStack(material));
          entity.setBillboard(spin ? Display.Billboard.FIXED : Display.Billboard.CENTER);
          decorate(entity, glow);
          tag(entity, id);
          entity.setTransformation(new Transformation(
              new Vector3f(0f, finalOffset, 0f),
              new Quaternionf(),
              new Vector3f(finalScale, finalScale, finalScale),
              new Quaternionf()
          ));
        });
        iconId = item.getUniqueId();
      }
    }
    float hitWidth = (float) yaml().getDouble("holo-blocks.hitbox-width", 1.05D);
    float hitHeight = (float) yaml().getDouble("holo-blocks.hitbox-height", 1.8D);
    Interaction hit = world.spawn(anchor, Interaction.class, entity -> {
      entity.setInteractionWidth(hitWidth);
      entity.setInteractionHeight(hitHeight);
      entity.setResponsive(true);
      entity.setPersistent(false);
      entity.setInvulnerable(true);
      tag(entity, id);
    });
    this.spawned.put(id, new Spawned(
        id,
        key(world.getName(), block.getBlockX(), block.getBlockY(), block.getBlockZ()),
        display.getUniqueId(),
        iconId,
        hit.getUniqueId(),
        block,
        offsetY,
        scale,
        iconScale,
        iconOffsetY,
        animation,
        holo.getString("particle", "none")
    ));
  }

  private void despawn(String id) {
    Spawned spawned = this.spawned.remove(id);
    if (spawned != null) {
      removeEntity(spawned.textId);
      removeEntity(spawned.iconId);
      removeEntity(spawned.hitId);
      purgeNearby(spawned.block, id);
    }
    purgeTagged(id);
  }

  private void purgeNearby(Location block, String id) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    World world = block.getWorld();
    Location anchor = block.clone().add(0.5, 0, 0.5);
    List<Entity> found = new ArrayList<>();
    found.addAll(world.getNearbyEntities(anchor, 1.75D, 3.5D, 1.75D));
    int chunkX = block.getBlockX() >> 4;
    int chunkZ = block.getBlockZ() >> 4;
    if (world.isChunkLoaded(chunkX, chunkZ)) {
      found.addAll(List.of(world.getChunkAt(chunkX, chunkZ).getEntities()));
    }
    NamespacedKey key = idKey();
    for (Entity entity : found) {
      if (!(entity instanceof TextDisplay) && !(entity instanceof ItemDisplay) && !(entity instanceof Interaction)) {
        continue;
      }
      String tagged = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
      if (tagged == null) {
        continue;
      }
      if (id == null || tagged.equalsIgnoreCase(id)) {
        entity.setPersistent(false);
        entity.remove();
      }
    }
  }

  private void removeHoloFromYaml(String id) {
    FileConfiguration yaml = yaml();
    ConfigurationSection holos = yaml.getConfigurationSection("holo-blocks.holos");
    if (holos == null) {
      yaml.set("holo-blocks.holos." + id, null);
      return;
    }
    Map<String, Map<String, Object>> kept = new LinkedHashMap<>();
    for (String key : holos.getKeys(false)) {
      if (key.equalsIgnoreCase(id)) {
        continue;
      }
      ConfigurationSection child = holos.getConfigurationSection(key);
      if (child != null) {
        kept.put(key, child.getValues(true));
      }
    }
    yaml.set("holo-blocks.holos", null);
    for (Map.Entry<String, Map<String, Object>> entry : kept.entrySet()) {
      yaml.createSection("holo-blocks.holos." + entry.getKey(), entry.getValue());
    }
  }

  private void purgeTagged(String id) {
    NamespacedKey key = idKey();
    for (World world : Bukkit.getWorlds()) {
      List<Entity> entities = new ArrayList<>();
      entities.addAll(world.getEntitiesByClass(TextDisplay.class));
      entities.addAll(world.getEntitiesByClass(ItemDisplay.class));
      entities.addAll(world.getEntitiesByClass(Interaction.class));
      for (Entity entity : entities) {
        String tagged = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tagged == null) {
          continue;
        }
        if (id == null || tagged.equalsIgnoreCase(id)) {
          entity.setPersistent(false);
          entity.remove();
        }
      }
    }
  }

  private void despawnAll() {
    for (String id : List.copyOf(this.spawned.keySet())) {
      despawn(id);
    }
  }

  private void mark(Player player, Location location) {
    this.selected.put(player.getUniqueId(), new Location(
        location.getWorld(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
    ));
    this.plugin.messages().send(player, "holos.block-set", Map.of(
        "world", location.getWorld() == null ? "-" : location.getWorld().getName(),
        "x", String.valueOf(location.getBlockX()),
        "y", String.valueOf(location.getBlockY()),
        "z", String.valueOf(location.getBlockZ())
    ));
  }

  private void setPreset(
      CommandSender sender,
      String name,
      String field,
      String section,
      String value,
      String okKey,
      String unknownKey
  ) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    String preset = value == null ? "none" : value.toLowerCase(Locale.ROOT);
    if (!preset.equals("none") && !keys(section).contains(preset)) {
      this.plugin.messages().send(sender, unknownKey, Map.of("value", preset));
      return;
    }
    yaml().set("holo-blocks.holos." + id + "." + field, preset);
    this.plugin.configs().saveMechanics();
    if (field.equals("animation")) {
      respawn(id);
    } else {
      Spawned spawned = this.spawned.get(id);
      if (spawned != null) {
        this.spawned.put(id, spawned.withFx(spawned.animation, field.equals("particle") ? preset : spawned.particle));
      } else {
        respawn(id);
      }
    }
    this.plugin.messages().send(sender, okKey, Map.of("holo", id, "value", preset));
  }

  private void setNumber(CommandSender sender, String name, String field, String raw, String okKey) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    double value;
    try {
      value = Double.parseDouble(raw);
    } catch (NumberFormatException | NullPointerException ignored) {
      this.plugin.messages().send(sender, "holos.invalid-number", Map.of("value", raw == null ? "" : raw));
      return;
    }
    yaml().set("holo-blocks.holos." + id + "." + field, value);
    this.plugin.configs().saveMechanics();
    respawn(id);
    this.plugin.messages().send(sender, okKey, Map.of("holo", id, "value", String.valueOf(value)));
  }

  private Component linesOf(ConfigurationSection hologram) {
    List<String> lines = hologram == null ? List.of() : hologram.getStringList("lines");
    if (lines.isEmpty()) {
      return Component.empty();
    }
    return this.plugin.messages().deserialize(String.join("<newline>", lines));
  }

  private void style(TextDisplay display, ConfigurationSection hologram, float scale, String animation, boolean glow) {
    ConfigurationSection section = hologram == null ? yaml().getConfigurationSection("holo-blocks") : hologram;
    boolean spin = "spin".equalsIgnoreCase(animation);
    display.setBillboard(spin
        ? Display.Billboard.FIXED
        : billboard(section == null ? "CENTER" : section.getString("billboard", "CENTER")));
    display.setSeeThrough(section != null && section.getBoolean("see-through", false));
    display.setLineWidth(section == null ? 200 : section.getInt("line-width", 200));
    display.setDefaultBackground(false);
    display.setBackgroundColor(Color.fromARGB(section == null ? 0 : section.getInt("background", 0)));
    display.setShadowed(section == null || section.getBoolean("shadowed", true));
    display.setAlignment(TextDisplay.TextAlignment.CENTER);
    decorate(display, glow);
    float offsetY = (float) (section == null ? 1.35D : section.getDouble("offset-y", 1.35D));
    display.setTransformation(new Transformation(
        new Vector3f(0f, offsetY, 0f),
        new Quaternionf(),
        new Vector3f(scale, scale, scale),
        new Quaternionf()
    ));
  }

  private static void decorate(Display display, boolean glow) {
    display.setPersistent(false);
    display.setInvulnerable(true);
    display.setShadowRadius(0f);
    display.setShadowStrength(0f);
    display.setGlowing(glow);
    if (glow) {
      display.setBrightness(new Display.Brightness(15, 15));
    }
  }

  private void tag(Entity entity, String id) {
    entity.getPersistentDataContainer().set(idKey(), PersistentDataType.STRING, id);
  }

  private static void apply(Display display, float x, float y, float z, float scale, Quaternionf rotation, int duration) {
    if (display == null) {
      return;
    }
    display.setInterpolationDelay(0);
    display.setInterpolationDuration(duration);
    display.setTransformation(new Transformation(
        new Vector3f(x, y, z),
        rotation,
        new Vector3f(scale, scale, scale),
        new Quaternionf()
    ));
  }

  private boolean nearby(Location at, double rangeSq) {
    for (Player player : at.getWorld().getPlayers()) {
      if (player.getLocation().distanceSquared(at) <= rangeSq) {
        return true;
      }
    }
    return false;
  }

  private String require(CommandSender sender, String name) {
    String id = yamlName(name);
    if (id == null) {
      this.plugin.messages().send(sender, "holos.unknown", Map.of("holo", name == null ? "" : name));
      return null;
    }
    return normalize(id);
  }

  private String yamlName(String name) {
    String id = normalize(name);
    ConfigurationSection holos = yaml().getConfigurationSection("holo-blocks.holos");
    if (holos == null) {
      return null;
    }
    if (holos.getConfigurationSection(id) != null) {
      return id;
    }
    for (String key : holos.getKeys(false)) {
      if (key.equalsIgnoreCase(id) || normalize(key).equals(id)) {
        return key;
      }
    }
    return null;
  }

  private Set<String> keys(String path) {
    ConfigurationSection section = yaml().getConfigurationSection(path);
    return section == null ? Set.of() : section.getKeys(false);
  }

  private FileConfiguration yaml() {
    return this.plugin.configs().mechanics();
  }

  private String commandLabel() {
    return this.plugin.configs().root().getString("commands.triggerblock", "triggerblock");
  }

  private boolean enabled() {
    return yaml().getBoolean("holo-blocks.enabled", true);
  }

  private NamespacedKey wandKey() {
    return new NamespacedKey(this.plugin, "holo_wand");
  }

  private NamespacedKey idKey() {
    return new NamespacedKey(this.plugin, "holo_block");
  }

  private static int blockCoord(ConfigurationSection section, String key) {
    if (section.contains(key)) {
      return (int) Math.floor(section.getDouble(key));
    }
    ConfigurationSection location = section.getConfigurationSection("location");
    if (location != null && location.contains(key)) {
      return (int) Math.floor(location.getDouble(key));
    }
    return 0;
  }

  private static String key(String world, int x, int y, int z) {
    return world.toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
  }

  private static String normalize(String name) {
    return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
  }

  private static Display.Billboard billboard(String raw) {
    try {
      return Display.Billboard.valueOf((raw == null ? "CENTER" : raw).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return Display.Billboard.CENTER;
    }
  }

  private <T> T entity(UUID id, Class<T> type) {
    Entity found = lookup(id);
    return type.isInstance(found) ? type.cast(found) : null;
  }

  private Entity lookup(UUID id) {
    if (id == null) {
      return null;
    }
    Entity entity = this.plugin.getServer().getEntity(id);
    if (entity != null) {
      return entity;
    }
    for (World world : Bukkit.getWorlds()) {
      entity = world.getEntity(id);
      if (entity != null) {
        return entity;
      }
    }
    return null;
  }

  private void removeEntity(UUID id) {
    Entity entity = lookup(id);
    if (entity != null) {
      entity.remove();
    }
  }

  private record Spawned(
      String id,
      String blockKey,
      UUID textId,
      UUID iconId,
      UUID hitId,
      Location block,
      float offsetY,
      float scale,
      float iconScale,
      float iconOffsetY,
      String animation,
      String particle
  ) {
    private Spawned withFx(String animation, String particle) {
      return new Spawned(
          this.id,
          this.blockKey,
          this.textId,
          this.iconId,
          this.hitId,
          this.block,
          this.offsetY,
          this.scale,
          this.iconScale,
          this.iconOffsetY,
          animation,
          particle
      );
    }
  }
}
