//made with love by nyxon

package com.modern.core.module.expeditions;

import com.modern.core.ModernCorePlugin;
import com.modern.core.config.YamlFile;
import com.modern.core.util.ActionCommands;
import com.modern.core.util.Durations;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class ExpeditionService {

  final ModernCorePlugin plugin;
  private final YamlFile file;
  private final ExpeditionRepository repository;
  private final Map<UUID, Optional<ExpeditionRepository.Run>> cache = new ConcurrentHashMap<>();
  private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
  private final Map<UUID, ClaimSession> sessions = new ConcurrentHashMap<>();
  private final Set<String> notified = ConcurrentHashMap.newKeySet();
  private final ExpeditionClaimMenu claimMenu;
  private volatile boolean ready;

  ExpeditionService(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.file = new YamlFile(plugin, "modules/expeditions.yml");
    this.file.load();
    this.repository = new ExpeditionRepository(plugin);
    this.claimMenu = new ExpeditionClaimMenu(this);
  }

  ExpeditionClaimMenu claimMenu() {
    return this.claimMenu;
  }

  void start() {
    Tasks.async(this.plugin, () -> {
      try {
        this.repository.createTable();
        this.ready = true;
        Tasks.sync(this.plugin, () -> {
          for (Player player : Bukkit.getOnlinePlayers()) {
            load(player, false);
          }
        });
      } catch (Exception exception) {
        this.plugin.getLogger().severe("Spedizioni: " + exception.getMessage());
      }
    });
  }

  void shutdown() {
    flushClaims();
    this.cache.clear();
    this.busy.clear();
    this.notified.clear();
    this.ready = false;
  }

  FileConfiguration config() {
    return this.file.config();
  }

  Optional<ExpeditionRepository.Run> state(UUID uuid) {
    return this.cache.get(uuid);
  }

  boolean loaded(UUID uuid) {
    return this.cache.containsKey(uuid);
  }

  void forget(UUID uuid) {
    this.cache.remove(uuid);
    this.busy.remove(uuid);
    String prefix = uuid + ":";
    this.notified.removeIf(key -> key.startsWith(prefix));
  }

  void load(Player player, boolean notify) {
    if (!this.ready) {
      return;
    }
    UUID uuid = player.getUniqueId();
    Tasks.async(this.plugin, () -> {
      try {
        Optional<ExpeditionRepository.Run> run = this.repository.find(uuid);
        this.cache.put(uuid, run);
        boolean readyNow = run.isPresent() && run.get().endsAt() <= System.currentTimeMillis();
        if (readyNow) {

          this.notified.add(notifyKey(uuid, run.get()));
        }
        if (notify && readyNow && config().getBoolean("settings.notify-on-join", true)) {
          String name = displayName(run.get().expedition());
          Tasks.sync(this.plugin, () -> {
            if (player.isOnline()) {
              send(player, "ready-join", Map.of("name", name));
              readySound(player);
            }
          });
        }
      } catch (Exception exception) {
        this.plugin.getLogger().warning("Spedizioni: lettura fallita per " + player.getName() + ": " + exception.getMessage());
      }
    });
  }

  long hours(Player player) {
    ConfigurationSection ranks = config().getConfigurationSection("ranks");
    if (ranks == null) {
      return -1;
    }
    long best = -1;
    for (String rank : ranks.getKeys(false)) {
      long hours = ranks.getLong(rank + ".hours", 0);
      if (hours <= 0) {
        continue;
      }
      String permission = ranks.getString(rank + ".permission", "group." + rank.toLowerCase(Locale.ROOT));
      if (permission != null && player.hasPermission(permission) && (best < 0 || hours < best)) {
        best = hours;
      }
    }
    return best;
  }

  int extraRolls(Player player) {
    ConfigurationSection ranks = config().getConfigurationSection("ranks");
    if (ranks == null) {
      return 0;
    }
    int best = 0;
    for (String rank : ranks.getKeys(false)) {
      int extra = ranks.getInt(rank + ".extra-rolls", 0);
      String permission = ranks.getString(rank + ".permission", "group." + rank.toLowerCase(Locale.ROOT));
      if (extra > best && permission != null && player.hasPermission(permission)) {
        best = extra;
      }
    }
    return best;
  }

  ConfigurationSection expedition(String id) {
    return id == null ? null : config().getConfigurationSection("expeditions." + id.toLowerCase(Locale.ROOT));
  }

  List<String> expeditionIds() {
    ConfigurationSection section = config().getConfigurationSection("expeditions");
    return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
  }

  boolean unlocked(Player player, ConfigurationSection expedition) {
    String permission = expedition.getString("required-permission", "");
    return permission == null || permission.isBlank() || player.hasPermission(permission);
  }

  String displayName(String expeditionId) {
    ConfigurationSection expedition = expedition(expeditionId);
    return expedition == null ? expeditionId : expedition.getString("name", expeditionId);
  }

  void startExpedition(Player player, String expeditionId, Runnable after) {
    UUID uuid = player.getUniqueId();
    ConfigurationSection expedition = expedition(expeditionId);
    if (expedition == null) {
      return;
    }
    long hours = hours(player);
    if (hours <= 0) {
      send(player, "no-rank", Map.of());
      return;
    }
    if (!unlocked(player, expedition)) {
      send(player, "locked", Map.of("hint", expedition.getString("locked-hint", "")));
      return;
    }
    if (!this.ready || !loaded(uuid)) {
      send(player, "loading", Map.of());
      load(player, false);
      return;
    }
    if (this.cache.get(uuid).isPresent()) {
      send(player, "already-running", Map.of());
      return;
    }
    if (!this.busy.add(uuid)) {
      return;
    }
    long now = System.currentTimeMillis();
    String id = expeditionId.toLowerCase(Locale.ROOT);
    ExpeditionRepository.Run run = new ExpeditionRepository.Run(id, now, now + hours * 3_600_000L);
    Tasks.async(this.plugin, () -> {
      boolean inserted = false;
      Optional<ExpeditionRepository.Run> current = Optional.empty();
      boolean failed = false;
      try {
        inserted = this.repository.insert(uuid, run);
        current = inserted ? Optional.of(run) : this.repository.find(uuid);
      } catch (Exception exception) {
        failed = true;
        this.plugin.getLogger().warning("Spedizioni: avvio fallito per " + player.getName() + ": " + exception.getMessage());
      }
      boolean ok = inserted;
      boolean error = failed;
      Optional<ExpeditionRepository.Run> state = current;
      Tasks.sync(this.plugin, () -> {
        this.busy.remove(uuid);
        if (!error) {
          this.cache.put(uuid, state);
        }
        if (!player.isOnline()) {
          return;
        }
        if (error) {
          send(player, "error", Map.of());
        } else if (ok) {
          send(player, "started", Map.of(
              "name", displayName(id),
              "duration", hours + "h"
          ));
        } else {
          send(player, "already-running", Map.of());
        }
        if (after != null) {
          after.run();
        }
      });
    });
  }

  ClaimSession session(UUID uuid) {
    return this.sessions.get(uuid);
  }

  void openClaim(Player player) {
    UUID uuid = player.getUniqueId();
    if (!this.ready || !loaded(uuid)) {
      send(player, "loading", Map.of());
      load(player, false);
      return;
    }
    ClaimSession existing = this.sessions.get(uuid);
    if (existing != null) {
      if (!existing.committing && existing.inventory != null) {
        player.openInventory(existing.inventory);
      }
      return;
    }
    Optional<ExpeditionRepository.Run> cached = this.cache.get(uuid);
    if (cached.isEmpty()) {
      return;
    }
    ExpeditionRepository.Run run = cached.get();
    if (run.endsAt() > System.currentTimeMillis()) {
      send(player, "not-ready", Map.of("remaining", Durations.formatRemaining(this.plugin, run.endsAt())));
      return;
    }
    if (this.busy.contains(uuid)) {
      return;
    }
    ConfigurationSection expedition = expedition(run.expedition());
    if (expedition == null) {
      this.plugin.getLogger().warning("Spedizioni: spedizione '" + run.expedition() + "' non più in config, riscatto bloccato per " + player.getName());
      send(player, "error", Map.of());
      return;
    }
    ClaimSession session = new ClaimSession(uuid, run, roll(player, expedition));
    this.sessions.put(uuid, session);
    this.claimMenu.open(player, session);
  }

  void claimAll(Player player) {
    ClaimSession session = this.sessions.get(player.getUniqueId());
    if (session == null || session.committing) {
      return;
    }
    this.claimMenu.revealAll(player, session);
    commit(player, session, false);
  }

  void closeClaim(Player player, boolean sync) {
    ClaimSession session = this.sessions.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    session.cancelTask();
    if (!session.committing) {
      commit(player, session, sync);
    }
  }

  void flushClaims() {
    for (ClaimSession session : new ArrayList<>(this.sessions.values())) {
      session.cancelTask();
      Player player = Bukkit.getPlayer(session.uuid);
      if (player != null && !session.committing) {
        commit(player, session, true);
      }
    }
    this.sessions.clear();
  }

  private void commit(Player player, ClaimSession session, boolean sync) {
    if (session.committing) {
      return;
    }
    session.committing = true;
    UUID uuid = session.uuid;
    this.busy.add(uuid);
    long now = System.currentTimeMillis();
    if (sync) {
      boolean claimed = false;
      boolean error = false;
      try {
        claimed = this.repository.claim(uuid, session.run, now);
      } catch (Exception exception) {
        error = true;
        this.plugin.getLogger().warning("Spedizioni: riscatto fallito per " + player.getName() + ": " + exception.getMessage());
      }
      complete(player, session, claimed, error, null);
      return;
    }
    Tasks.async(this.plugin, () -> {
      boolean claimed = false;
      boolean failed = false;
      Optional<ExpeditionRepository.Run> fresh = null;
      try {
        claimed = this.repository.claim(uuid, session.run, now);
        if (!claimed) {
          fresh = this.repository.find(uuid);
        }
      } catch (Exception exception) {
        failed = true;
        this.plugin.getLogger().warning("Spedizioni: riscatto fallito per " + player.getName() + ": " + exception.getMessage());
      }
      boolean ok = claimed;
      boolean error = failed;
      Optional<ExpeditionRepository.Run> state = fresh;
      Tasks.sync(this.plugin, () -> complete(player, session, ok, error, state));
    });
  }

  private void complete(Player player, ClaimSession session, boolean claimed, boolean error,
              Optional<ExpeditionRepository.Run> fresh) {
    UUID uuid = session.uuid;
    this.sessions.remove(uuid, session);
    try {
      if (error) {

        if (player.isOnline()) {
          send(player, "error", Map.of());
          closeIfOpen(player, session);
        }
        return;
      }
      if (!claimed) {

        if (fresh != null) {
          this.cache.put(uuid, fresh);
        } else {
          this.cache.remove(uuid);
        }
        if (player.isOnline()) {
          send(player, "claim-gone", Map.of());
          closeIfOpen(player, session);
        }
        return;
      }
      this.cache.put(uuid, Optional.empty());
      this.notified.remove(notifyKey(uuid, session.run));
      if (!player.isOnline()) {

        this.cache.remove(uuid);
        ExpeditionRepository.Run run = session.run;
        Tasks.async(this.plugin, () -> {
          try {
            this.repository.insert(uuid, run);
          } catch (Exception exception) {
            this.plugin.getLogger().warning("Spedizioni: ripristino fallito per " + uuid + " (" + run.expedition() + "): " + exception.getMessage());
          }
        });
        return;
      }
      grant(player, session);
    } finally {
      this.busy.remove(uuid);
    }
  }

  private void grant(Player player, ClaimSession session) {
    session.granted = true;
    boolean full = player.getInventory().firstEmpty() < 0;
    for (ClaimSession.Reward reward : session.rewards) {
      ActionCommands.run(this.plugin, player, reward.commands());
    }
    send(player, "claimed", Map.of("name", displayName(session.run.expedition())));
    for (ClaimSession.Reward reward : session.rewards) {
      send(player, reward.bonus() ? "roll" : "reward-line", Map.of("reward", reward.label()));
    }
    if (full || player.getInventory().firstEmpty() < 0) {
      send(player, "inventory-full", Map.of());
    }
    this.claimMenu.sound(player, "claim", 1.0f);
    this.claimMenu.markClaimed(player, session);
  }

  private void closeIfOpen(Player player, ClaimSession session) {
    if (session.inventory != null && player.getOpenInventory().getTopInventory() == session.inventory) {
      player.closeInventory();
    }
  }

  private static String notifyKey(UUID uuid, ExpeditionRepository.Run run) {
    return uuid + ":" + run.expedition() + ":" + run.endsAt();
  }

  void notifyCompleted() {
    if (!this.ready) {
      return;
    }
    long now = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID uuid = player.getUniqueId();
      Optional<ExpeditionRepository.Run> state = this.cache.get(uuid);
      if (state == null || state.isEmpty() || state.get().endsAt() > now || this.sessions.containsKey(uuid)) {
        continue;
      }
      if (this.notified.add(notifyKey(uuid, state.get()))) {
        send(player, "ready-now", Map.of("name", displayName(state.get().expedition())));
        readySound(player);
      }
    }
  }

  private void readySound(Player player) {
    String sound = config().getString("settings.ready-sound", "");
    if (sound != null && !sound.isBlank()) {
      com.modern.core.util.Sounds.playRaw(this.plugin, player, sound, "expeditions.settings.ready-sound");
    }
  }

  void finishNow(OfflinePlayer target, Consumer<Boolean> result) {
    UUID uuid = target.getUniqueId();
    Tasks.async(this.plugin, () -> {
      boolean done;
      Optional<ExpeditionRepository.Run> fresh = Optional.empty();
      try {
        done = this.repository.finishNow(uuid, System.currentTimeMillis());
        fresh = this.repository.find(uuid);
      } catch (Exception exception) {
        done = false;
        this.plugin.getLogger().warning("Spedizioni: completamento admin fallito: " + exception.getMessage());
      }
      boolean ok = done;
      Optional<ExpeditionRepository.Run> state = fresh;
      Tasks.sync(this.plugin, () -> {
        if (this.cache.containsKey(uuid)) {
          this.cache.put(uuid, state);
        }
        result.accept(ok);
      });
    });
  }

  private List<ClaimSession.Reward> roll(Player player, ConfigurationSection expedition) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    List<ClaimSession.Reward> rewards = new ArrayList<>();
    for (String line : randomize(expedition.getStringList("guaranteed"), random)) {
      if (line == null || line.isBlank()) {
        continue;
      }
      rewards.add(this.claimMenu.reward(List.of(line), null, null, false, false));
    }
    int rolls = Math.max(0, expedition.getInt("rolls", 1)) + extraRolls(player);
    List<Map<?, ?>> pool = expedition.getMapList("pool");
    List<Integer> weights = new ArrayList<>();
    for (Map<?, ?> entry : pool) {
      weights.add(asInt(entry.get("weight"), 0));
    }
    int rareWeight = config().getInt("claim-gui.rare-weight", 10);
    for (int i = 0; i < rolls; i++) {
      int index = ExpeditionRules.pickWeighted(weights, random);
      if (index < 0) {
        break;
      }
      Map<?, ?> entry = pool.get(index);
      List<String> lines = new ArrayList<>(strings(entry.get("commands")));
      List<String> randomLines = strings(entry.get("random-commands"));
      if (!randomLines.isEmpty()) {
        lines.add(randomLines.get(random.nextInt(randomLines.size())));
      }
      Object name = entry.get("name");
      Object icon = entry.get("icon");
      Object rareFlag = entry.get("rare");
      boolean rare = rareFlag != null ? Boolean.parseBoolean(String.valueOf(rareFlag)) : weights.get(index) <= rareWeight;
      rewards.add(this.claimMenu.reward(randomize(lines, random),
          name == null ? null : String.valueOf(name),
          icon == null ? null : String.valueOf(icon),
          rare, true));
    }
    return rewards;
  }

  private static List<String> randomize(List<String> lines, ThreadLocalRandom random) {
    List<String> out = new ArrayList<>(lines.size());
    for (String line : lines) {
      out.add(ExpeditionRules.fillRandom(line, random));
    }
    return out;
  }

  private static List<String> strings(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        if (value != null) {
          out.add(String.valueOf(value));
        }
      }
    }
    return out;
  }

  private static int asInt(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  String text(String raw, Map<String, String> placeholders) {
    String prefix = this.plugin.configs().root().getString("plugin.prefix", "");
    String out = raw == null ? "" : raw.replace("{prefix}", prefix);
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
    }
    return out;
  }

  void send(CommandSender sender, String key, Map<String, String> placeholders) {
    String raw = config().getString("messages." + key, "");
    if (raw == null || raw.isBlank()) {
      return;
    }
    Map<String, String> values = new HashMap<>(placeholders);
    values.putIfAbsent("player", sender.getName());
    sender.sendMessage(this.plugin.messages().deserialize(text(raw, values)));
  }
}
