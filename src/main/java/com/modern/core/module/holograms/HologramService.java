package com.modern.core.module.holograms;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramService {

  private final ModernCorePlugin plugin;
  private final HologramStorage storage;
  private final Map<String, YamlConfiguration> files = new ConcurrentHashMap<>();
  private final Map<String, UUID> shared = new ConcurrentHashMap<>();
  private final Map<String, Map<UUID, UUID>> personal = new ConcurrentHashMap<>();
  private final Set<String> spawning = ConcurrentHashMap.newKeySet();
  private BukkitTask task;
  private int ticks;

  public HologramService(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.storage = new HologramStorage(plugin);
  }

  public void initialize() {
    this.storage.initialize();
    start();
  }

  public void start() {
    stop();
    if (!settings().getBoolean("enabled", true)) {
      return;
    }
    this.files.clear();
    this.files.putAll(this.storage.loadAll());
    spawnLoaded();
    int interval = Math.max(1, settings().getInt("tick-ticks", 10));
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, interval, interval);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    this.ticks = 0;
    for (String id : List.copyOf(this.files.keySet())) {
      despawn(id);
    }
    despawnAll();
    purgeTagged(null);
    this.files.clear();
  }

  public void unload(UUID playerId) {
    for (Map<UUID, UUID> copies : this.personal.values()) {
      UUID entityId = copies.remove(playerId);
      removeEntity(entityId);
    }
  }

  public Set<String> names() {
    return Set.copyOf(this.files.keySet());
  }

  public void create(Player player, String name) {
    String id = HologramStorage.normalize(name);
    if (id.isBlank()) {
      this.plugin.messages().send(player, "holograms.invalid-name");
      return;
    }
    if (this.files.containsKey(id)) {
      this.plugin.messages().send(player, "holograms.exists", Map.of("holo", id));
      return;
    }
    if (this.files.size() >= settings().getInt("max", 200)) {
      this.plugin.messages().send(player, "holograms.max");
      return;
    }
    YamlConfiguration yaml = defaultsAt(player.getLocation());
    this.storage.save(id, yaml);
    this.files.put(id, yaml);
    respawn(id);
    this.plugin.messages().send(player, "holograms.created", Map.of("holo", id));
  }

  public void delete(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    despawn(id);
    purgeTagged(id);
    this.files.remove(id);
    this.storage.delete(id);
    this.plugin.messages().send(sender, "holograms.deleted", Map.of("holo", id));
  }

  public void list(CommandSender sender) {
    if (this.files.isEmpty()) {
      this.plugin.messages().send(sender, "holograms.list-empty");
      return;
    }
    List<String> lines = new ArrayList<>();
    for (String id : this.files.keySet()) {
      boolean on = yaml(id).getBoolean("enabled", true);
      lines.add(id + (on ? "" : " (off)"));
    }
    this.plugin.messages().send(sender, "holograms.list", Map.of(
        "count", String.valueOf(lines.size()),
        "holos", String.join(", ", lines)
    ));
  }

  public void info(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    FileConfiguration yaml = yaml(id);
    Location at = locationOf(yaml);
    this.plugin.messages().send(sender, "holograms.info", Map.of(
        "holo", id,
        "world", at == null || at.getWorld() == null ? yaml.getString("location.world", "-") : at.getWorld().getName(),
        "x", String.format(Locale.ROOT, "%.2f", yaml.getDouble("location.x")),
        "y", String.format(Locale.ROOT, "%.2f", yaml.getDouble("location.y")),
        "z", String.format(Locale.ROOT, "%.2f", yaml.getDouble("location.z")),
        "scale", String.valueOf(yaml.getDouble("scale_x", 1.0D)),
        "billboard", yaml.getString("billboard", "center"),
        "lines", String.valueOf(yaml.getStringList("text").size())
    ));
  }

  public void near(Player player) {
    double range = settings().getDouble("near-range", 16.0D);
    double rangeSq = range * range;
    List<String> found = new ArrayList<>();
    for (String id : this.files.keySet()) {
      Location at = locationOf(yaml(id));
      if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
        continue;
      }
      if (at.distanceSquared(player.getLocation()) <= rangeSq) {
        found.add(id);
      }
    }
    if (found.isEmpty()) {
      this.plugin.messages().send(player, "holograms.near-empty", Map.of("range", String.valueOf((int) range)));
      return;
    }
    this.plugin.messages().send(player, "holograms.near", Map.of(
        "count", String.valueOf(found.size()),
        "holos", String.join(", ", found)
    ));
  }

  public void moveHere(Player player, String name) {
    String id = require(player, name);
    if (id == null) {
      return;
    }
    writeLocation(yaml(id), player.getLocation());
    saveAndRespawn(id);
    this.plugin.messages().send(player, "holograms.moved", Map.of("holo", id));
  }

  public void teleport(Player player, String name) {
    String id = require(player, name);
    if (id == null) {
      return;
    }
    Location at = locationOf(yaml(id));
    if (at == null || at.getWorld() == null) {
      this.plugin.messages().send(player, "holograms.invalid-location", Map.of("holo", id));
      return;
    }
    player.teleport(at);
    this.plugin.messages().send(player, "holograms.teleported", Map.of("holo", id));
  }

  public void enable(CommandSender sender, String name, boolean on) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml(id).set("enabled", on);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, on ? "holograms.enabled" : "holograms.disabled", Map.of("holo", id));
  }

  public void addLine(CommandSender sender, String name, String text) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    if (text == null || text.isBlank()) {
      this.plugin.messages().send(sender, "holograms.line-usage", Map.of("label", label()));
      return;
    }
    List<String> lines = new ArrayList<>(yaml(id).getStringList("text"));
    lines.add(text);
    yaml(id).set("text", lines);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.line-set", Map.of("holo", id));
  }

  public void setLine(CommandSender sender, String name, String indexRaw, String text) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    int index = indexOf(sender, indexRaw);
    List<String> lines = new ArrayList<>(yaml(id).getStringList("text"));
    if (index < 0 || index >= lines.size()) {
      this.plugin.messages().send(sender, "holograms.line-missing", Map.of("holo", id, "index", String.valueOf(index)));
      return;
    }
    lines.set(index, text == null ? "" : text);
    yaml(id).set("text", lines);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.line-set", Map.of("holo", id));
  }

  public void removeLine(CommandSender sender, String name, String indexRaw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    int index = indexOf(sender, indexRaw);
    List<String> lines = new ArrayList<>(yaml(id).getStringList("text"));
    if (index < 0 || index >= lines.size()) {
      this.plugin.messages().send(sender, "holograms.line-missing", Map.of("holo", id, "index", String.valueOf(index)));
      return;
    }
    lines.remove(index);
    yaml(id).set("text", lines);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.line-removed", Map.of("holo", id, "index", String.valueOf(index)));
  }

  public void insertLine(CommandSender sender, String name, String indexRaw, String text) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    int index = Math.max(0, indexOf(sender, indexRaw));
    List<String> lines = new ArrayList<>(yaml(id).getStringList("text"));
    if (index > lines.size()) {
      index = lines.size();
    }
    lines.add(index, text == null ? "" : text);
    yaml(id).set("text", lines);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.line-set", Map.of("holo", id));
  }

  public void clearLines(CommandSender sender, String name) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml(id).set("text", List.of());
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.lines-cleared", Map.of("holo", id));
  }

  public void setScale(CommandSender sender, String name, String xRaw, String yRaw, String zRaw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Double x = number(sender, xRaw);
    if (x == null) {
      return;
    }
    double y = yRaw == null || yRaw.isBlank() ? x : parseOr(sender, yRaw, x);
    double z = zRaw == null || zRaw.isBlank() ? x : parseOr(sender, zRaw, x);
    yaml(id).set("scale_x", x);
    yaml(id).set("scale_y", y);
    yaml(id).set("scale_z", z);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.scale-set", Map.of("holo", id, "value", x + " " + y + " " + z));
  }

  public void setBillboard(CommandSender sender, String name, String value) {
    setString(sender, name, "billboard", value, "holograms.billboard-set");
  }

  public void setShadow(CommandSender sender, String name, String radiusRaw, String strengthRaw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Double radius = number(sender, radiusRaw);
    if (radius == null) {
      return;
    }
    double strength = strengthRaw == null || strengthRaw.isBlank() ? yaml(id).getDouble("shadow_strength", 1.0D) : parseOr(sender, strengthRaw, 1.0D);
    yaml(id).set("shadow_radius", radius);
    yaml(id).set("shadow_strength", strength);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.shadow-set", Map.of("holo", id, "value", radius + " " + strength));
  }

  public void setBackground(CommandSender sender, String name, String value) {
    setString(sender, name, "background", value, "holograms.background-set");
  }

  public void setSeeThrough(CommandSender sender, String name, String raw) {
    setBoolean(sender, name, "see_through", raw, "holograms.seethrough-set");
  }

  public void setTextShadow(CommandSender sender, String name, String raw) {
    setBoolean(sender, name, "text_shadow", raw, "holograms.textshadow-set");
  }

  public void setAlignment(CommandSender sender, String name, String value) {
    setString(sender, name, "text_alignment", value, "holograms.alignment-set");
  }

  public void setBrightness(CommandSender sender, String name, String blockRaw, String skyRaw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Integer block = integer(sender, blockRaw);
    if (block == null) {
      return;
    }
    int sky = skyRaw == null || skyRaw.isBlank() ? yaml(id).getInt("sky_brightness", 15) : parseIntOr(sender, skyRaw, 15);
    yaml(id).set("block_brightness", Math.max(-1, Math.min(15, block)));
    yaml(id).set("sky_brightness", Math.max(-1, Math.min(15, sky)));
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.brightness-set", Map.of("holo", id, "value", block + " " + sky));
  }

  public void setDistance(CommandSender sender, String name, String raw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Double value = number(sender, raw);
    if (value == null) {
      return;
    }
    yaml(id).set("visibility_distance", value);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.distance-set", Map.of("holo", id, "value", String.valueOf(value)));
  }

  public void setUpdateInterval(CommandSender sender, String name, String raw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Integer value = integer(sender, raw);
    if (value == null) {
      return;
    }
    yaml(id).set("update_text_interval", value);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.interval-set", Map.of("holo", id, "value", String.valueOf(value)));
  }

  public void setYaw(CommandSender sender, String name, String raw) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    Double value = number(sender, raw);
    if (value == null) {
      return;
    }
    yaml(id).set("location.yaw", value);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, "holograms.yaw-set", Map.of("holo", id, "value", String.valueOf(value)));
  }

  public void copy(CommandSender sender, String from, String to) {
    String id = require(sender, from);
    if (id == null) {
      return;
    }
    String dest = HologramStorage.normalize(to);
    if (dest.isBlank()) {
      this.plugin.messages().send(sender, "holograms.invalid-name");
      return;
    }
    if (this.files.containsKey(dest)) {
      this.plugin.messages().send(sender, "holograms.exists", Map.of("holo", dest));
      return;
    }
    YamlConfiguration copy = new YamlConfiguration();
    for (String key : yaml(id).getKeys(true)) {
      if (!yaml(id).isConfigurationSection(key)) {
        copy.set(key, yaml(id).get(key));
      }
    }
    this.storage.save(dest, copy);
    this.files.put(dest, copy);
    respawn(dest);
    this.plugin.messages().send(sender, "holograms.copied", Map.of("holo", id, "target", dest));
  }

  public int importFancy(CommandSender sender, String path) {
    File source;
    if (path == null || path.isBlank()) {
      source = new File(this.storage.folder(), "holograms.yml");
    } else {
      source = new File(this.plugin.getDataFolder(), path);
      if (!source.isFile()) {
        source = new File(path);
      }
    }
    int created = this.storage.importFancy(source);
    start();
    this.plugin.messages().send(sender, "holograms.imported", Map.of("count", String.valueOf(created)));
    return created;
  }

  public void onChunkLoad(World world, int chunkX, int chunkZ) {
    for (String id : this.files.keySet()) {
      FileConfiguration yaml = yaml(id);
      if (!yaml.getBoolean("enabled", true) || HologramTexts.personal(yaml.getStringList("text"))) {
        continue;
      }
      Location at = locationOf(yaml);
      if (at == null || at.getWorld() == null || !at.getWorld().equals(world)) {
        continue;
      }
      if ((at.getBlockX() >> 4) == chunkX && (at.getBlockZ() >> 4) == chunkZ) {
        spawnShared(id, yaml);
      }
    }
  }

  public void onChunkUnload(World world, int chunkX, int chunkZ) {
    for (String id : List.copyOf(this.shared.keySet())) {
      Location at = locationOf(yaml(id));
      if (at == null || at.getWorld() == null || !at.getWorld().equals(world)) {
        continue;
      }
      if ((at.getBlockX() >> 4) == chunkX && (at.getBlockZ() >> 4) == chunkZ) {
        despawn(id);
      }
    }
  }

  private void tick() {
    this.ticks++;
    int personalCap = settings().getInt("max-personal-displays", 200);
    int used = personalCount();
    for (Map.Entry<String, YamlConfiguration> entry : this.files.entrySet()) {
      String id = entry.getKey();
      FileConfiguration yaml = entry.getValue();
      if (!yaml.getBoolean("enabled", true)) {
        continue;
      }
      List<String> lines = yaml.getStringList("text");
      boolean perPlayer = HologramTexts.personal(lines);
      int interval = yaml.getInt("update_text_interval", -1);
      boolean refresh = interval > 0 && this.ticks % Math.max(1, interval) == 0;
      if (perPlayer) {
        updatePersonal(id, yaml, lines, used, personalCap, refresh);
        used = personalCount();
      } else if (refresh) {
        applyText(entity(this.shared.get(id), TextDisplay.class), null, lines);
      }
    }
  }

  private void updatePersonal(
      String id,
      FileConfiguration yaml,
      List<String> lines,
      int used,
      int cap,
      boolean refresh
  ) {
    Location at = locationOf(yaml);
    if (at == null || at.getWorld() == null) {
      return;
    }
    double range = viewBlocks(yaml);
    double rangeSq = range * range;
    Map<UUID, UUID> copies = this.personal.computeIfAbsent(id, key -> new ConcurrentHashMap<>());
    for (UUID playerId : List.copyOf(copies.keySet())) {
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline() || !player.getWorld().equals(at.getWorld())
          || player.getLocation().distanceSquared(at) > rangeSq) {
        removeEntity(copies.remove(playerId));
      }
    }
    for (Player player : at.getWorld().getPlayers()) {
      if (player.getLocation().distanceSquared(at) > rangeSq) {
        continue;
      }
      UUID existing = copies.get(player.getUniqueId());
      TextDisplay display = entity(existing, TextDisplay.class);
      if (display == null) {
        if (used >= cap) {
          continue;
        }
        copies.remove(player.getUniqueId());
        purgeAt(id, at);
        display = spawnDisplay(id, at, yaml, player, lines);
        copies.put(player.getUniqueId(), display.getUniqueId());
        player.showEntity(this.plugin, display);
        used++;
      } else if (refresh) {
        applyText(display, player, lines);
      }
    }
  }

  private void spawnLoaded() {
    for (String id : this.files.keySet()) {
      FileConfiguration yaml = yaml(id);
      if (!yaml.getBoolean("enabled", true) || HologramTexts.personal(yaml.getStringList("text"))) {
        continue;
      }
      spawnShared(id, yaml);
    }
  }

  private void spawnShared(String id, FileConfiguration yaml) {
    if (!this.spawning.add(id)) {
      return;
    }
    try {
      spawnSharedNow(id, yaml);
    } finally {
      this.spawning.remove(id);
    }
  }

  private void spawnSharedNow(String id, FileConfiguration yaml) {
    Location at = locationOf(yaml);
    despawn(id);
    if (at == null || at.getWorld() == null) {
      return;
    }
    at.getWorld().getChunkAt(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    purgeAt(id, at);
    TextDisplay display = spawnDisplay(id, at, yaml, null, yaml.getStringList("text"));
    this.shared.put(id, display.getUniqueId());
  }

  private TextDisplay spawnDisplay(String id, Location at, FileConfiguration yaml, Player viewer, List<String> lines) {
    return at.getWorld().spawn(at, TextDisplay.class, display -> {
      display.setPersistent(false);
      display.setInvulnerable(true);
      display.getPersistentDataContainer().set(idKey(), PersistentDataType.STRING, id);
      display.setBillboard(billboard(yaml.getString("billboard", "center")));
      display.setSeeThrough(yaml.getBoolean("see_through", false));
      display.setShadowed(yaml.getBoolean("text_shadow", true));
      display.setDefaultBackground(false);
      display.setBackgroundColor(background(yaml.getString("background", "transparent")));
      display.setAlignment(alignment(yaml.getString("text_alignment", "center")));
      display.setLineWidth(yaml.getInt("line_width", settings().getInt("line-width", 200)));
      display.setShadowRadius((float) yaml.getDouble("shadow_radius", 0.0D));
      display.setShadowStrength((float) yaml.getDouble("shadow_strength", 1.0D));
      display.setViewRange((float) (viewBlocks(yaml) / 64.0D));
      int block = yaml.getInt("block_brightness", -1);
      int sky = yaml.getInt("sky_brightness", -1);
      if (block >= 0 || sky >= 0) {
        display.setBrightness(new Display.Brightness(Math.max(0, block), Math.max(0, sky)));
      }
      display.setRotation(at.getYaw(), at.getPitch());
      float sx = (float) yaml.getDouble("scale_x", 1.0D);
      float sy = (float) yaml.getDouble("scale_y", 1.0D);
      float sz = (float) yaml.getDouble("scale_z", 1.0D);
      float tx = (float) yaml.getDouble("translation_x", 0.0D);
      float ty = (float) yaml.getDouble("translation_y", 0.0D);
      float tz = (float) yaml.getDouble("translation_z", 0.0D);
      display.setTransformation(new Transformation(
          new Vector3f(tx, ty, tz),
          new Quaternionf(),
          new Vector3f(sx, sy, sz),
          new Quaternionf()
      ));
      if (viewer != null) {
        display.setVisibleByDefault(false);
      }
      applyText(display, viewer, lines);
    });
  }

  private void applyText(TextDisplay display, Player viewer, List<String> lines) {
    if (display == null) {
      return;
    }
    Component text = HologramTexts.parse(this.plugin, viewer, lines);
    display.text(text);
  }

  private void respawn(String id) {
    despawn(id);
    FileConfiguration yaml = yaml(id);
    if (yaml != null && yaml.getBoolean("enabled", true) && !HologramTexts.personal(yaml.getStringList("text"))) {
      spawnShared(id, yaml);
    }
  }

  private void despawn(String id) {
    removeEntity(this.shared.remove(id));
    Map<UUID, UUID> copies = this.personal.remove(id);
    if (copies != null) {
      for (UUID entityId : copies.values()) {
        removeEntity(entityId);
      }
    }
    FileConfiguration yaml = yaml(id);
    if (yaml != null) {
      purgeAt(id, locationOf(yaml));
    }
    purgeTagged(id);
  }

  private void purgeAt(String id, Location at) {
    if (id == null || at == null || at.getWorld() == null) {
      return;
    }
    Set<UUID> keep = owned(id);
    NamespacedKey key = idKey();
    List<Entity> found = new ArrayList<>();
    at.getWorld().getChunkAt(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    found.addAll(at.getWorld().getNearbyEntities(at, 1.25D, 1.25D, 1.25D));
    int chunkX = at.getBlockX() >> 4;
    int chunkZ = at.getBlockZ() >> 4;
    if (at.getWorld().isChunkLoaded(chunkX, chunkZ)) {
      found.addAll(List.of(at.getWorld().getChunkAt(chunkX, chunkZ).getEntities()));
    }
    for (Entity entity : found) {
      if (!(entity instanceof TextDisplay) || keep.contains(entity.getUniqueId())) {
        continue;
      }
      String tagged = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
      if (id.equalsIgnoreCase(tagged) || (tagged == null && !entity.isPersistent())) {
        entity.setPersistent(false);
        entity.remove();
      }
    }
  }

  private Set<UUID> owned(String id) {
    Set<UUID> keep = new HashSet<>();
    UUID sharedId = this.shared.get(id);
    if (sharedId != null) {
      keep.add(sharedId);
    }
    Map<UUID, UUID> copies = this.personal.get(id);
    if (copies != null) {
      keep.addAll(copies.values());
    }
    return keep;
  }

  private void purgeTagged(String id) {
    NamespacedKey key = idKey();
    for (World world : Bukkit.getWorlds()) {
      for (TextDisplay display : List.copyOf(world.getEntitiesByClass(TextDisplay.class))) {
        String tagged = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tagged == null) {
          continue;
        }
        if (id == null || tagged.equalsIgnoreCase(id)) {
          display.remove();
        }
      }
    }
  }

  private void despawnAll() {
    for (String id : List.copyOf(this.shared.keySet())) {
      despawn(id);
    }
    for (String id : List.copyOf(this.personal.keySet())) {
      despawn(id);
    }
  }

  private void saveAndRespawn(String id) {
    this.storage.save(id, yaml(id));
    respawn(id);
  }

  private YamlConfiguration defaultsAt(Location at) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("enabled", true);
    yaml.set("type", "TEXT");
    writeLocation(yaml, at);
    yaml.set("visibility_distance", settings().getDouble("default-visibility-distance", 64.0D));
    yaml.set("visibility", "ALL");
    yaml.set("scale_x", 1.0D);
    yaml.set("scale_y", 1.0D);
    yaml.set("scale_z", 1.0D);
    yaml.set("translation_x", 0.0D);
    yaml.set("translation_y", 0.0D);
    yaml.set("translation_z", 0.0D);
    yaml.set("shadow_radius", 0.0D);
    yaml.set("shadow_strength", 1.0D);
    yaml.set("text", List.of("<gray>Hologram", "<white>/vholo"));
    yaml.set("text_shadow", true);
    yaml.set("see_through", false);
    yaml.set("text_alignment", "center");
    yaml.set("update_text_interval", -1);
    yaml.set("background", "transparent");
    yaml.set("billboard", "center");
    yaml.set("block_brightness", -1);
    yaml.set("sky_brightness", -1);
    yaml.set("line_width", settings().getInt("line-width", 200));
    return yaml;
  }

  private static void writeLocation(FileConfiguration yaml, Location at) {
    yaml.set("location.world", at.getWorld() == null ? "world" : at.getWorld().getName());
    yaml.set("location.x", at.getX());
    yaml.set("location.y", at.getY());
    yaml.set("location.z", at.getZ());
    yaml.set("location.yaw", at.getYaw());
    yaml.set("location.pitch", at.getPitch());
  }

  private Location locationOf(FileConfiguration yaml) {
    if (yaml == null) {
      return null;
    }
    ConfigurationSection section = yaml.getConfigurationSection("location");
    if (section == null) {
      return null;
    }
    World world = Bukkit.getWorld(section.getString("world", ""));
    if (world == null) {
      return null;
    }
    return new Location(
        world,
        section.getDouble("x"),
        section.getDouble("y"),
        section.getDouble("z"),
        (float) section.getDouble("yaw", 0.0D),
        (float) section.getDouble("pitch", 0.0D)
    );
  }

  private double viewBlocks(FileConfiguration yaml) {
    double distance = yaml.getDouble("visibility_distance", settings().getDouble("default-visibility-distance", 64.0D));
    if (distance < 0) {
      return settings().getDouble("unlimited-view-blocks", 128.0D);
    }
    return Math.max(8.0D, distance);
  }

  private void setString(CommandSender sender, String name, String field, String value, String message) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    yaml(id).set(field, value == null ? "" : value.toLowerCase(Locale.ROOT));
    saveAndRespawn(id);
    this.plugin.messages().send(sender, message, Map.of("holo", id, "value", value == null ? "" : value));
  }

  private void setBoolean(CommandSender sender, String name, String field, String raw, String message) {
    String id = require(sender, name);
    if (id == null) {
      return;
    }
    boolean on = raw != null && (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("si"));
    yaml(id).set(field, on);
    saveAndRespawn(id);
    this.plugin.messages().send(sender, message, Map.of("holo", id, "value", on ? "on" : "off"));
  }

  private String require(CommandSender sender, String name) {
    String id = HologramStorage.normalize(name);
    if (!this.files.containsKey(id)) {
      this.plugin.messages().send(sender, "holograms.unknown", Map.of("holo", name == null ? "" : name));
      return null;
    }
    return id;
  }

  private FileConfiguration yaml(String id) {
    return this.files.get(id);
  }

  private FileConfiguration settings() {
    return this.plugin.configs().holograms();
  }

  private String label() {
    return this.plugin.configs().root().getString("commands.holo", "vholo");
  }

  private NamespacedKey idKey() {
    return new NamespacedKey(this.plugin, "holo_display");
  }

  private int personalCount() {
    int total = 0;
    for (Map<UUID, UUID> copies : this.personal.values()) {
      total += copies.size();
    }
    return total;
  }

  private Double number(CommandSender sender, String raw) {
    try {
      return Double.parseDouble(raw);
    } catch (NumberFormatException | NullPointerException ignored) {
      this.plugin.messages().send(sender, "holograms.invalid-number", Map.of("value", raw == null ? "" : raw));
      return null;
    }
  }

  private Integer integer(CommandSender sender, String raw) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException | NullPointerException ignored) {
      this.plugin.messages().send(sender, "holograms.invalid-number", Map.of("value", raw == null ? "" : raw));
      return null;
    }
  }

  private double parseOr(CommandSender sender, String raw, double fallback) {
    Double value = number(sender, raw);
    return value == null ? fallback : value;
  }

  private int parseIntOr(CommandSender sender, String raw, int fallback) {
    Integer value = integer(sender, raw);
    return value == null ? fallback : value;
  }

  private int indexOf(CommandSender sender, String raw) {
    Integer value = integer(sender, raw);
    return value == null ? -1 : value;
  }

  private static Display.Billboard billboard(String raw) {
    String token = raw == null ? "CENTER" : raw.toUpperCase(Locale.ROOT);
    if (token.equals("HORIZONTAL")) {
      return Display.Billboard.HORIZONTAL;
    }
    if (token.equals("VERTICAL")) {
      return Display.Billboard.VERTICAL;
    }
    if (token.equals("FIXED")) {
      return Display.Billboard.FIXED;
    }
    try {
      return Display.Billboard.valueOf(token);
    } catch (IllegalArgumentException ignored) {
      return Display.Billboard.CENTER;
    }
  }

  private static TextDisplay.TextAlignment alignment(String raw) {
    try {
      return TextDisplay.TextAlignment.valueOf((raw == null ? "CENTER" : raw).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return TextDisplay.TextAlignment.CENTER;
    }
  }

  private static Color background(String raw) {
    if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("transparent")) {
      return Color.fromARGB(0);
    }
    String token = raw.startsWith("#") ? raw.substring(1) : raw;
    try {
      if (token.length() == 8) {
        return Color.fromARGB((int) Long.parseLong(token, 16));
      }
      if (token.length() == 6) {
        return Color.fromARGB(0xFF000000 | Integer.parseInt(token, 16));
      }
    } catch (NumberFormatException ignored) {
      return Color.fromARGB(0);
    }
    return Color.fromARGB(0);
  }

  private <T> T entity(UUID id, Class<T> type) {
    if (id == null) {
      return null;
    }
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
}
