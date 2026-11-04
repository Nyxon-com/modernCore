package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PlaytimeService {

  private final ModernCorePlugin plugin;
  private final PlayerDataRepository repository;
  private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

  public PlaytimeService(ModernCorePlugin plugin, PlayerDataRepository repository) {
    this.plugin = plugin;
    this.repository = repository;
  }

  public void startSession(UUID uuid) {
    this.sessionStart.put(uuid, System.currentTimeMillis());
  }

  public long endSession(UUID uuid) {
    Long started = this.sessionStart.remove(uuid);
    if (started == null) {
      return 0L;
    }
    return Math.max(0L, System.currentTimeMillis() - started);
  }

  public long liveMillis(UUID uuid, long storedMs) {
    Long started = this.sessionStart.get(uuid);
    if (started == null) {
      return storedMs;
    }
    return storedMs + Math.max(0L, System.currentTimeMillis() - started);
  }

  public String format(long millis) {
    long totalMinutes = Math.max(0L, TimeUnit.MILLISECONDS.toMinutes(millis));
    long days = totalMinutes / (60L * 24L);
    long hours = (totalMinutes / 60L) % 24L;
    long minutes = totalMinutes % 60L;
    if (days > 0) {
      return this.plugin.messages().text("playtime.format-days", Map.of(
          "days", String.valueOf(days),
          "hours", String.valueOf(hours),
          "minutes", String.valueOf(minutes)
      ));
    }
    if (hours > 0) {
      return this.plugin.messages().text("playtime.format-hours", Map.of(
          "hours", String.valueOf(hours),
          "minutes", String.valueOf(minutes)
      ));
    }
    return this.plugin.messages().text("playtime.format-minutes", Map.of(
        "minutes", String.valueOf(Math.max(1L, minutes == 0 && millis > 0 ? 1L : minutes))
    ));
  }

  public PlayerDataRepository repository() {
    return this.repository;
  }
}
