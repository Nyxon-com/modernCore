package com.valaryrpg.core.module.expeditions;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.config.YamlFile;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.Durations;
import com.valaryrpg.core.util.Tasks;
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

/**
 * Avvio/riscatto spedizioni. Stato in cache RAM, SQL solo async, claim atomico (DELETE == 1).
 */
public final class ExpeditionService {

    final ValaryCorePlugin plugin;
    private final YamlFile file;
    private final ExpeditionRepository repository;
    private final Map<UUID, Optional<ExpeditionRepository.Run>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile boolean ready;

    ExpeditionService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "modules/expeditions.yml");
        this.file.load();
        this.repository = new ExpeditionRepository(plugin);
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
        this.cache.clear();
        this.busy.clear();
        this.ready = false;
    }

    FileConfiguration config() {
        return this.file.config();
    }

    // ------------------------------------------------------------------ stato

    /** Stato in cache; null se non ancora caricato. */
    Optional<ExpeditionRepository.Run> state(UUID uuid) {
        return this.cache.get(uuid);
    }

    boolean loaded(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

    void forget(UUID uuid) {
        this.cache.remove(uuid);
        this.busy.remove(uuid);
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
                if (notify && run.isPresent() && run.get().endsAt() <= System.currentTimeMillis()
                        && config().getBoolean("settings.notify-on-join", true)) {
                    Tasks.sync(this.plugin, () -> {
                        if (player.isOnline()) {
                            send(player, "ready-join", Map.of());
                        }
                    });
                }
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Spedizioni: lettura fallita per " + player.getName() + ": " + exception.getMessage());
            }
        });
    }

    /** Ore di durata per il player (la più corta tra i rank posseduti), -1 se non idoneo. */
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

    // ----------------------------------------------------------------- azioni

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

    void claim(Player player, Runnable after) {
        UUID uuid = player.getUniqueId();
        if (!this.ready || !loaded(uuid)) {
            send(player, "loading", Map.of());
            load(player, false);
            return;
        }
        Optional<ExpeditionRepository.Run> cached = this.cache.get(uuid);
        if (cached.isEmpty()) {
            return;
        }
        ExpeditionRepository.Run run = cached.get();
        long now = System.currentTimeMillis();
        if (run.endsAt() > now) {
            send(player, "not-ready", Map.of("remaining", Durations.formatRemaining(this.plugin, run.endsAt())));
            return;
        }
        if (!this.busy.add(uuid)) {
            return;
        }
        Tasks.async(this.plugin, () -> {
            boolean claimed = false;
            boolean failed = false;
            Optional<ExpeditionRepository.Run> fresh = Optional.empty();
            try {
                claimed = this.repository.claim(uuid, run, now);
                if (!claimed) {
                    fresh = this.repository.find(uuid);
                }
            } catch (Exception exception) {
                failed = true;
                this.plugin.getLogger().warning("Spedizioni: riscatto fallito per " + player.getName() + ": " + exception.getMessage());
            }
            boolean ok = claimed;
            boolean error = failed;
            Optional<ExpeditionRepository.Run> state = ok ? Optional.empty() : fresh;
            Tasks.sync(this.plugin, () -> {
                try {
                    if (!error) {
                        this.cache.put(uuid, state);
                    }
                    if (error) {
                        if (player.isOnline()) {
                            send(player, "error", Map.of());
                        }
                        return;
                    }
                    if (!ok) {
                        return;
                    }
                    if (!player.isOnline()) {
                        // uscito durante il riscatto: ripristina la riga, potrà riscattare al prossimo login
                        this.cache.remove(uuid);
                        Tasks.async(this.plugin, () -> {
                            try {
                                this.repository.insert(uuid, run);
                            } catch (Exception exception) {
                                this.plugin.getLogger().warning("Spedizioni: ripristino fallito per " + uuid + " (" + run.expedition() + "): " + exception.getMessage());
                            }
                        });
                        return;
                    }
                    reward(player, run.expedition());
                } finally {
                    this.busy.remove(uuid);
                    if (after != null && player.isOnline()) {
                        after.run();
                    }
                }
            });
        });
    }

    /** Admin: completa subito la spedizione di un player. */
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

    private void reward(Player player, String expeditionId) {
        ConfigurationSection expedition = expedition(expeditionId);
        if (expedition == null) {
            this.plugin.getLogger().warning("Spedizioni: spedizione '" + expeditionId + "' non più in config, nessun premio per " + player.getName());
            send(player, "error", Map.of());
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ActionCommands.run(this.plugin, player, randomize(expedition.getStringList("guaranteed"), random));
        int rolls = Math.max(0, expedition.getInt("rolls", 1)) + extraRolls(player);
        List<Map<?, ?>> pool = expedition.getMapList("pool");
        List<Integer> weights = new ArrayList<>();
        for (Map<?, ?> entry : pool) {
            weights.add(asInt(entry.get("weight"), 0));
        }
        send(player, "claimed", Map.of("name", displayName(expeditionId)));
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
            ActionCommands.run(this.plugin, player, randomize(lines, random));
            Object name = entry.get("name");
            send(player, "roll", Map.of("reward", name == null ? "?" : String.valueOf(name)));
        }
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

    // --------------------------------------------------------------- messaggi

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
