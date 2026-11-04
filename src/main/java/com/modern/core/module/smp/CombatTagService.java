package com.modern.core.module.smp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTagService {
  private final SmpContext plugin;
  private final Map<UUID, Long> until = new ConcurrentHashMap<>();

  public CombatTagService(SmpContext plugin) {
    this.plugin = plugin;
  }

  public boolean isTagged(Player player) {
    Long end = until.get(player.getUniqueId());
    return end != null && end > System.currentTimeMillis();
  }

  public void tag(Player player) {
    boolean fresh = !isTagged(player);
    int seconds = Math.max(1, plugin.getConfig().getInt("combat-seconds", 15));
    until.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    if (fresh) {
      SmpText.send(player, "&cSei in combattimento. Non uscire e non teletrasportarti.");
    }
  }

  public void clear(Player player) {
    until.remove(player.getUniqueId());
  }

  public void tick() {
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      Player player = plugin.getServer().getPlayer(entry.getKey());
      if (entry.getValue() <= now || player == null || !player.isOnline()) {
        it.remove();
        if (player != null && player.isOnline()) {
          SmpText.send(player, "&aNon sei piu in combattimento.");
        }
        continue;
      }
      long left = Math.max(1L, (entry.getValue() - now + 999L) / 1000L);
      player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
          .deserialize("&cCombattimento: &f" + left + "s"));
    }
  }

  public boolean isBlocked(String message) {
    String raw = message.toLowerCase(Locale.ROOT).trim();
    if (raw.startsWith("/")) {
      raw = raw.substring(1);
    }
    int space = raw.indexOf(' ');
    String head = space < 0 ? raw : raw.substring(0, space);
    int colon = head.indexOf(':');
    if (colon >= 0) {
      head = head.substring(colon + 1);
      raw = head + (space < 0 ? "" : raw.substring(space));
    }
    for (String entry : plugin.getConfig().getStringList("combat-blocked-commands")) {
      String blocked = entry.toLowerCase(Locale.ROOT).trim();
      if (blocked.isEmpty()) {
        continue;
      }
      if (raw.equals(blocked) || raw.startsWith(blocked + " ")) {
        return true;
      }
    }
    return false;
  }
}
