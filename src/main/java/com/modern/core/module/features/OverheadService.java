//made with love by nyxon

package com.modern.core.module.features;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.moderation.ModerationModule;
import com.modern.core.module.moderation.VanishService;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OverheadService implements Listener {

  private static final long WINDOW_MS = 3000L;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final Pattern BRACES = Pattern.compile("\\{([A-Za-z0-9_-]+)}");
  private static final String SHEET = "(({mmoitems_stat_attack_damage}*{mmoitems_stat_attack_speed}) * (1-{mmoitems_stat_critical-strike-chance}/100) + (({mmoitems_stat_attack_damage}*{mmoitems_stat_attack_speed})*({mmoitems_stat_critical_strike_power}/100)*({mmoitems_stat_critical-strike-chance}/100))) * (1 + {mmoitems_stat_weapon-damage} / 100) * (1 + {mythiclib_stat_physical_damage} / 100)";

  private final ModernCorePlugin plugin;
  private final Map<UUID, TextDisplay> labels = new ConcurrentHashMap<>();
  private final Map<UUID, Deque<Hit>> hits = new ConcurrentHashMap<>();
  private final ManaLookup mana = new ManaLookup();
  private BukkitTask task;

  public OverheadService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      ensure(player);
    }
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 10L, 2L);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    for (TextDisplay display : this.labels.values()) {
      if (display != null && display.isValid()) {
        display.remove();
      }
    }
    this.labels.clear();
    this.hits.clear();
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    ensure(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onWorld(PlayerChangedWorldEvent event) {
    remove(event.getPlayer().getUniqueId());
    ensure(event.getPlayer());
  }

  @EventHandler(ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    Player attacker = attacker(event.getDamager());
    if (attacker == null || event.getFinalDamage() <= 0) {
      return;
    }
    Deque<Hit> window = this.hits.computeIfAbsent(attacker.getUniqueId(), id -> new ArrayDeque<>());
    window.addLast(new Hit(System.currentTimeMillis(), event.getFinalDamage()));
  }

  private void tick() {
    long now = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (vanished(player)) {
        remove(player.getUniqueId());
        continue;
      }
      TextDisplay display = ensure(player);
      if (display == null) {
        continue;
      }
      display.text(MINI.deserialize(lines(player, now)));
      player.hideEntity(this.plugin, display);
      Location location = player.getLocation().clone();
      location.setPitch(0);
      location.add(0, player.getHeight() + 0.45D, 0);
      display.teleport(location);
    }
  }

  private TextDisplay ensure(Player player) {
    TextDisplay current = this.labels.get(player.getUniqueId());
    if (current != null && current.isValid() && current.getWorld().equals(player.getWorld())) {
      return current;
    }
    if (current != null) {
      current.remove();
    }
    Location location = player.getLocation().clone();
    location.setPitch(0);
    location.add(0, player.getHeight() + 0.45D, 0);
    TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
      entity.text(MINI.deserialize(lines(player, System.currentTimeMillis())));
      entity.setBillboard(Display.Billboard.FIXED);
      entity.setAlignment(TextDisplay.TextAlignment.CENTER);
      entity.setSeeThrough(false);
      entity.setShadowed(true);
      entity.setDefaultBackground(false);
      entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
      entity.setLineWidth(420);
      entity.setPersistent(false);
      entity.setTeleportDuration(3);
      entity.setViewRange(1.5F);
      org.bukkit.util.Transformation transformation = entity.getTransformation();
      transformation.getScale().set(1.0F, 1.0F, 1.0F);
      entity.setTransformation(transformation);
    });
    player.hideEntity(this.plugin, display);
    this.labels.put(player.getUniqueId(), display);
    return display;
  }

  private String lines(Player player, long now) {
    double health = Math.max(0, player.getHealth());
    AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
    double cap = maxHealth == null ? 20 : maxHealth.getValue();
    double[] mana = this.mana.read(player);
    return "\uA415 <red>" + one(health) + "</red><dark_gray>/</dark_gray><gray>" + one(cap) + "</gray>  "
        + "\uA41C <aqua>" + one(mana[0]) + "</aqua><dark_gray>/</dark_gray><dark_aqua>" + one(mana[1]) + "</dark_aqua>  "
        + "\uA41E <yellow>" + sheet(player, now) + "</yellow> <gray>\u1D05\u1D18\uA731</gray>";
  }

  private static String one(double value) {
    return String.format(Locale.US, "%.1f", value);
  }

  private String sheet(Player player, long now) {
    String parsed = sheetValue(player);
    if (parsed != null) {
      try {
        return String.format(Locale.US, "%.2f", Double.parseDouble(parsed.replace(',', '.')));
      } catch (NumberFormatException ignored) {
      }
    }
    return String.format(Locale.US, "%.2f", dps(player.getUniqueId(), now));
  }

  private String sheetValue(Player player) {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
      return null;
    }
    Matcher matcher = BRACES.matcher(SHEET);
    String numbers = matcher.replaceAll(match -> {
      String got = PlaceholderAPI.setPlaceholders(player, "%" + match.group(1) + "%");
      if (got == null || !got.matches("-?\\d+(?:\\.\\d+)?")) {
        return "0";
      }
      return got;
    });
    String parsed = PlaceholderAPI.setPlaceholders(player, "%math_2:1_" + numbers + "%");
    if (parsed == null || parsed.indexOf('%') >= 0) {
      return null;
    }
    return parsed.trim();
  }

  private double dps(UUID id, long now) {
    Deque<Hit> window = this.hits.get(id);
    if (window == null) {
      return 0;
    }
    while (!window.isEmpty() && now - window.peekFirst().at > WINDOW_MS) {
      window.removeFirst();
    }
    double total = 0;
    for (Hit hit : window) {
      total += hit.amount;
    }
    return total / 3.0D;
  }

  private void remove(UUID id) {
    TextDisplay display = this.labels.remove(id);
    if (display != null && display.isValid()) {
      display.remove();
    }
    this.hits.remove(id);
  }

  private boolean vanished(Player player) {
    ModerationModule moderation = this.plugin.modules().get("moderation");
    if (moderation == null) {
      return false;
    }
    VanishService vanish = moderation.vanish();
    return vanish != null && vanish.isVanished(player);
  }

  private Player attacker(Entity entity) {
    if (entity instanceof Player player) {
      return player;
    }
    if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  private record Hit(long at, double amount) {
  }

  private static final class ManaLookup {

    private Method get;
    private boolean byPlayer;
    private Method mana;
    private Method stats;
    private Method stat;
    private boolean unavailable;
    private boolean logged;

    double[] read(Player player) {
      if (this.unavailable) {
        return new double[] {0, 0};
      }
      try {
        if (this.get == null) {
          bind();
        }
        Object data = this.byPlayer ? this.get.invoke(null, player) : this.get.invoke(null, player.getUniqueId());
        if (data == null) {
          return new double[] {0, 0};
        }
        double current = ((Number) this.mana.invoke(data)).doubleValue();
        Object bag = this.stats.invoke(data);
        double max = ((Number) this.stat.invoke(bag, "MAX_MANA")).doubleValue();
        return new double[] {current, max};
      } catch (Exception exception) {
        if (!this.logged) {
          this.logged = true;
          this.unavailable = true;
          Bukkit.getLogger().warning("Mana sopra la testa non disponibile: " + exception.getMessage());
        }
        return new double[] {0, 0};
      }
    }

    private void bind() throws ReflectiveOperationException {
      Class<?> type = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
      try {
        this.get = type.getMethod("get", UUID.class);
        this.byPlayer = false;
      } catch (NoSuchMethodException exception) {
        this.get = type.getMethod("get", Player.class);
        this.byPlayer = true;
      }
      this.mana = type.getMethod("getMana");
      this.stats = type.getMethod("getStats");
      this.stat = this.stats.getReturnType().getMethod("getStat", String.class);
    }
  }
}
