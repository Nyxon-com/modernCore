package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.worlds.WorldsModule;
import com.modern.core.util.Tasks;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameModeService {

  private final ModernCorePlugin plugin;
  private final PlayerDataRepository repository;
  private final Map<UUID, GameMode> modes = new ConcurrentHashMap<>();
  private final Set<UUID> paused = ConcurrentHashMap.newKeySet();
  private final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);

  public GameModeService(ModernCorePlugin plugin, PlayerDataRepository repository) {
    this.plugin = plugin;
    this.repository = repository;
  }

  public void restore(Player player, String stored) {
    if (!this.plugin.configs().players().getBoolean("gamemode.persist", true)) {
      return;
    }
    if (forced(player)) {
      return;
    }
    GameMode mode = parse(stored, player.getGameMode());
    this.modes.put(player.getUniqueId(), mode);
    set(player, mode);
  }

  public void remember(Player player) {
    if (Boolean.TRUE.equals(this.applying.get()) || this.paused.contains(player.getUniqueId())) {
      return;
    }
    if (!this.plugin.configs().players().getBoolean("gamemode.persist", true)) {
      return;
    }
    GameMode mode = player.getGameMode();
    this.modes.put(player.getUniqueId(), mode);
    Tasks.async(this.plugin, () -> this.repository.updateGamemode(player.getUniqueId(), player.getName(), mode.name()));
  }

  public void followWorld(Player player) {
    if (!this.plugin.configs().players().getBoolean("gamemode.persist", true)) {
      return;
    }
    holdThenReapply(player);
  }

  public void pauseRespawn(Player player) {
    if (!persistDeath()) {
      return;
    }
    this.paused.add(player.getUniqueId());
  }

  public void restoreAfterRespawn(Player player) {
    if (!persistDeath()) {
      return;
    }
    holdThenReapply(player);
  }

  private void holdThenReapply(Player player) {
    UUID uuid = player.getUniqueId();
    this.paused.add(uuid);
    reapply(player);
    Tasks.nextTick(this.plugin, () -> reapply(player));
    Tasks.later(this.plugin, 2L, () -> reapply(player));
    Tasks.later(this.plugin, 10L, () -> {
      this.paused.remove(uuid);
      reapply(player);
    });
  }

  private boolean persistDeath() {
    return this.plugin.configs().players().getBoolean("gamemode.persist-death", true);
  }

  public void reapply(Player player) {
    if (!player.isOnline() || forced(player)) {
      return;
    }
    GameMode mode = this.modes.get(player.getUniqueId());
    if (mode != null) {
      set(player, mode);
    }
  }

  public void unload(UUID uuid) {
    this.modes.remove(uuid);
    this.paused.remove(uuid);
  }

  private void set(Player player, GameMode mode) {
    this.applying.set(true);
    try {
      player.setGameMode(mode);
    } finally {
      this.applying.set(false);
    }
  }

  private boolean forced(Player player) {
    if (player.getWorld() == null) {
      return false;
    }
    WorldsModule worlds = this.plugin.modules().get("worlds");
    return worlds != null && worlds.catalog() != null && worlds.catalog().forceGamemode(player.getWorld());
  }

  private static GameMode parse(String raw, GameMode fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return fallback;
    }
  }
}
