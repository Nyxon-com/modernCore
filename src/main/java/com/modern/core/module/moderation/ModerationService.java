package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.players.PlayerDataRepository;
import com.modern.core.util.Durations;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationService {

  private final ModernCorePlugin plugin;
  private final PunishmentRepository repository;
  private final PlayerDataRepository players;
  private final BanHoldService holds;
  private final PunishmentFormat format;
  private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

  public ModerationService(
      ModernCorePlugin plugin,
      PunishmentRepository repository,
      PlayerDataRepository players,
      BanHoldService holds
  ) {
    this.plugin = plugin;
    this.repository = repository;
    this.players = players;
    this.holds = holds;
    this.format = new PunishmentFormat(plugin);
  }

  public void kick(CommandSender staff, Player target, String reason) {
    String finalReason = reasonOrDefault(reason, "kick.default-reason");
    Tasks.async(this.plugin, () -> this.repository.insert(
        PunishmentType.KICK, target.getUniqueId(), target.getName(), ip(target), staff.getName(), finalReason, null
    ));
    target.kick(DisconnectScreens.kick(this.plugin, target.getName(), staff.getName(), finalReason));
    this.plugin.messages().send(staff, "moderation.kick-staff", Map.of(
        "player", target.getName(),
        "reason", this.plugin.messages().escape(finalReason)
    ));
    logAction("Kick", staff.getName(), target.getName(), finalReason, "");
  }

  public void ban(CommandSender staff, String targetName, String durationRaw, String reason) {
    Target target = resolve(targetName);
    String finalReason = reasonOrDefault(reason, "ban.default-reason");
    Long expiry = parseExpiry(durationRaw);
    Tasks.async(this.plugin, () -> this.repository.insert(
        PunishmentType.BAN, target.uuid, target.name, target.ip, staff.getName(), finalReason, expiry
    ));
    if (target.online != null) {
      disconnect(target.online, new Punishment(
          -1L,
          PunishmentType.BAN,
          target.uuid,
          target.name,
          target.ip,
          staff.getName(),
          finalReason,
          System.currentTimeMillis(),
          expiry,
          true,
          null,
          null,
          null
      ));
    }
    this.plugin.messages().send(staff, "moderation.ban-staff", Map.of(
        "player", target.name,
        "reason", this.plugin.messages().escape(finalReason),
        "duration", Durations.formatRemaining(this.plugin, expiry)
    ));
    logAction("Ban", staff.getName(), target.name, finalReason, Durations.formatRemaining(this.plugin, expiry));
  }

  public void ipBan(CommandSender staff, String targetOrIp, String durationRaw, String reason) {
    String ip = targetOrIp;
    String name = targetOrIp;
    UUID uuid = null;
    Player online = Bukkit.getPlayerExact(targetOrIp);
    if (online != null) {
      uuid = online.getUniqueId();
      name = online.getName();
      ip = ip(online);
    } else if (!looksLikeIp(targetOrIp)) {
      Target target = resolve(targetOrIp);
      uuid = target.uuid;
      name = target.name;
      ip = target.ip.isBlank() ? targetOrIp : target.ip;
    }
    String finalReason = reasonOrDefault(reason, "ipban.default-reason");
    Long expiry = parseExpiry(durationRaw);
    UUID bannedUuid = uuid;
    String bannedName = name;
    String bannedIp = ip;
    Tasks.async(this.plugin, () -> this.repository.insert(
        PunishmentType.IPBAN, bannedUuid, bannedName, bannedIp, staff.getName(), finalReason, expiry
    ));
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (bannedIp.equals(ip(player))) {
        disconnect(player, new Punishment(
            -1L,
            PunishmentType.IPBAN,
            player.getUniqueId(),
            player.getName(),
            bannedIp,
            staff.getName(),
            finalReason,
            System.currentTimeMillis(),
            expiry,
            true,
            null,
            null,
            null
        ));
      }
    }
    this.plugin.messages().send(staff, "moderation.ipban-staff", Map.of(
        "target", bannedName,
        "ip", bannedIp,
        "reason", this.plugin.messages().escape(finalReason),
        "duration", Durations.formatRemaining(this.plugin, expiry)
    ));
    logAction("IP ban", staff.getName(), bannedName + " (" + bannedIp + ")", finalReason, Durations.formatRemaining(this.plugin, expiry));
  }

  public void unban(CommandSender staff, String target, String reason) {
    if (reason == null || reason.isBlank()) {
      this.plugin.messages().send(staff, "moderation.unban-usage", Map.of("label", "pardon"));
      return;
    }
    String finalReason = reason.trim();
    Target resolved = looksLikeIp(target) ? new Target(null, target, target, null) : resolve(target);
    String ip = resolved.ip;
    if (ip.isBlank() && resolved.uuid != null) {
      ip = this.players.lastIp(resolved.uuid);
    }
    int bans = this.repository.pardon(PunishmentType.BAN, resolved.uuid, resolved.name, ip, staff.getName(), finalReason);
    int ips = this.repository.pardon(PunishmentType.IPBAN, resolved.uuid, resolved.name, ip, staff.getName(), finalReason);
    if (bans + ips <= 0) {
      this.plugin.messages().send(staff, "moderation.unban-missing", Map.of("player", target));
      return;
    }
    this.holds.unban(resolved.uuid);
    if (resolved.online != null) {
      this.holds.unban(resolved.online.getUniqueId());
    }
    if (ips > 0) {
      this.plugin.messages().send(staff, "moderation.unban-ip-staff", Map.of(
          "player", resolved.name,
          "ip", ip.isBlank() ? target : ip,
          "reason", this.plugin.messages().escape(finalReason)
      ));
      logAction("Pardon IP", staff.getName(), resolved.name, finalReason, ip.isBlank() ? target : ip);
      return;
    }
    this.plugin.messages().send(staff, "moderation.unban-staff", Map.of(
        "player", resolved.name,
        "reason", this.plugin.messages().escape(finalReason)
    ));
    logAction("Pardon", staff.getName(), resolved.name, finalReason, "");
  }

  public void pardonList(CommandSender staff) {
    Tasks.async(this.plugin, () -> {
      List<Punishment> rows = this.repository.listPardons();
      int max = Math.max(1, this.plugin.configs().moderation().getInt("history.max-listed", 25));
      List<Punishment> shown = rows.size() > max ? rows.subList(0, max) : rows;
      Tasks.sync(this.plugin, () -> {
        if (rows.isEmpty()) {
          this.plugin.messages().send(staff, "moderation.pardon-list-empty");
          return;
        }
        this.plugin.messages().send(staff, "moderation.pardon-list-header", Map.of("count", String.valueOf(rows.size())));
        for (Punishment row : shown) {
          this.plugin.messages().send(staff, "moderation.pardon-list-line", this.format.linePlaceholders(row));
        }
      });
    });
  }

  public void history(CommandSender staff, String targetName) {
    Target target = looksLikeIp(targetName) ? new Target(null, targetName, targetName, null) : resolve(targetName);
    Tasks.async(this.plugin, () -> {
      String ip = target.ip;
      if (ip.isBlank() && target.uuid != null) {
        ip = this.players.lastIp(target.uuid);
      }
      List<Punishment> rows = looksLikeIp(targetName)
          ? this.repository.historyByIp(ip)
          : this.repository.history(target.uuid, target.name, ip);
      int max = Math.max(1, this.plugin.configs().moderation().getInt("history.max-listed", 25));
      List<Punishment> shown = rows.size() > max ? rows.subList(0, max) : rows;
      String shownIp = ip == null || ip.isBlank() ? "-" : ip;
      Tasks.sync(this.plugin, () -> {
        if (rows.isEmpty()) {
          this.plugin.messages().send(staff, "moderation.history-empty", Map.of("player", target.name));
          return;
        }
          this.plugin.messages().send(staff, "moderation.history-header", Map.of(
            "player", target.name,
            "count", String.valueOf(rows.size()),
            "ip", shownIp
        ));
        for (Punishment row : shown) {
          this.plugin.messages().send(staff, this.format.historyLineKey(row), this.format.linePlaceholders(row));
        }
      });
    });
  }

  public void mute(CommandSender staff, String targetName, String durationRaw, String reason) {
    Target target = resolve(targetName);
    String finalReason = reasonOrDefault(reason, "mute.default-reason");
    Long expiry = parseExpiry(durationRaw);
    Punishment mute = new Punishment(-1L, PunishmentType.MUTE, target.uuid, target.name, target.ip, staff.getName(), finalReason, System.currentTimeMillis(), expiry, true, null, null, null);
    if (target.uuid != null) {
      this.mutes.put(target.uuid, mute);
    }
    Tasks.async(this.plugin, () -> this.repository.insert(PunishmentType.MUTE, target.uuid, target.name, target.ip, staff.getName(), finalReason, expiry));
    if (target.online != null) {
      this.plugin.messages().send(target.online, "moderation.mute-target", Map.of(
          "reason", this.plugin.messages().escape(finalReason),
          "duration", Durations.formatRemaining(this.plugin, expiry)
      ));
    }
    this.plugin.messages().send(staff, "moderation.mute-staff", Map.of(
        "player", target.name,
        "reason", this.plugin.messages().escape(finalReason),
        "duration", Durations.formatRemaining(this.plugin, expiry)
    ));
    logAction("Mute", staff.getName(), target.name, finalReason, Durations.formatRemaining(this.plugin, expiry));
  }

  public void unmute(CommandSender staff, String targetName) {
    Target target = resolve(targetName);
    if (target.uuid != null) {
      this.mutes.remove(target.uuid);
    }
    Tasks.async(this.plugin, () -> {
      int updated = this.repository.deactivate(PunishmentType.MUTE, target.uuid, target.name, target.ip);
      Tasks.sync(this.plugin, () -> {
        if (updated <= 0) {
          this.plugin.messages().send(staff, "moderation.unmute-missing", Map.of("player", target.name));
          return;
        }
        this.plugin.messages().send(staff, "moderation.unmute-staff", Map.of("player", target.name));
        logAction("Unmute", staff.getName(), target.name, "", "");
      });
    });
  }

  public void warn(CommandSender staff, String targetName, String reason) {
    Target target = resolve(targetName);
    String finalReason = reasonOrDefault(reason, "warn.default-reason");
    Tasks.async(this.plugin, () -> this.repository.insert(
        PunishmentType.WARN, target.uuid, target.name, target.ip, staff.getName(), finalReason, null
    ));
    if (target.online != null) {
      this.plugin.messages().send(target.online, "moderation.warn-target", Map.of(
          "reason", this.plugin.messages().escape(finalReason),
          "staff", this.plugin.messages().escape(staff.getName())
      ));
    }
    this.plugin.messages().send(staff, "moderation.warn-staff", Map.of(
        "player", target.name,
        "reason", this.plugin.messages().escape(finalReason)
    ));
    logAction("Warn", staff.getName(), target.name, finalReason, "");
  }

  public void warnings(CommandSender staff, String targetName) {
    Target target = resolve(targetName);
    Tasks.async(this.plugin, () -> {
      List<Punishment> history = this.repository.history(target.uuid, target.name, PunishmentType.WARN);
      int shown = Math.min(history.size(), this.plugin.configs().moderation().getInt("warn.max-listed", 10));
      Tasks.sync(this.plugin, () -> {
        if (history.isEmpty()) {
          this.plugin.messages().send(staff, "moderation.warnings-empty", Map.of("player", target.name));
          return;
        }
        this.plugin.messages().send(staff, "moderation.warnings-header", Map.of(
            "player", target.name,
            "count", String.valueOf(history.size())
        ));
        for (int i = 0; i < shown; i++) {
          Punishment punishment = history.get(i);
          this.plugin.messages().send(staff, "moderation.warnings-line", Map.of(
              "id", String.valueOf(punishment.id()),
              "reason", this.plugin.messages().escape(punishment.reason()),
              "staff", this.plugin.messages().escape(punishment.staff()),
              "when", Durations.formatWhen(this.plugin, punishment.createdAt())
          ));
        }
      });
    });
  }

  public void removeWarn(CommandSender staff, String targetName, String which) {
    Target target = resolve(targetName);
    Tasks.async(this.plugin, () -> {
      List<Punishment> history = this.repository.history(target.uuid, target.name, PunishmentType.WARN);
      if (history.isEmpty()) {
        Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "moderation.warn-remove-missing", Map.of("player", target.name)));
        return;
      }
      String token = which == null ? "" : which.trim();
      if (token.equalsIgnoreCase("all") || token.equals("*")) {
        int count = this.repository.deactivate(PunishmentType.WARN, target.uuid, target.name, target.ip);
        Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "moderation.warn-removed-all", Map.of(
            "player", target.name,
            "count", String.valueOf(count)
        )));
        return;
      }
      Punishment picked = pickWarn(history, token);
      if (picked == null) {
        Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "moderation.warn-remove-unknown", Map.of(
            "player", target.name,
            "id", token.isBlank() ? "-" : token
        )));
        return;
      }
      this.repository.deactivate(picked.id());
      Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "moderation.warn-removed", Map.of(
          "player", target.name,
          "id", String.valueOf(picked.id())
      )));
    });
  }

  public void banList(CommandSender staff) {
    Tasks.async(this.plugin, () -> {
      List<Punishment> bans = this.repository.listActive(PunishmentType.BAN);
      List<Punishment> ips = this.repository.listActive(PunishmentType.IPBAN);
      Tasks.sync(this.plugin, () -> {
        if (bans.isEmpty() && ips.isEmpty()) {
          this.plugin.messages().send(staff, "moderation.banlist-empty");
          return;
        }
        this.plugin.messages().send(staff, "moderation.banlist-header", Map.of(
            "bans", String.valueOf(bans.size()),
            "ips", String.valueOf(ips.size())
        ));
        for (Punishment ban : bans) {
          this.plugin.messages().send(staff, "moderation.banlist-line", this.format.banLine(ban));
        }
        for (Punishment ban : ips) {
          this.plugin.messages().send(staff, "moderation.banlist-ip-line", this.format.banLine(ban));
        }
      });
    });
  }

  public void dupeIp(CommandSender staff, String target) {
    Tasks.async(this.plugin, () -> {
      String ip;
      if (looksLikeIp(target)) {
        ip = target;
      } else {
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
          ip = ip(online);
        } else {
          Target resolved = resolve(target);
          ip = resolved.uuid != null ? this.players.lastIp(resolved.uuid) : "";
        }
      }
      if (ip == null || ip.isBlank()) {
        Tasks.sync(this.plugin, () -> this.plugin.messages().send(staff, "moderation.dupeip-missing", Map.of("player", target)));
        return;
      }
      java.util.List<String> names = this.players.namesByIp(ip);
      String listed = names.isEmpty() ? "-" : String.join(", ", names);
      java.util.List<String> lines = new java.util.ArrayList<>();
      for (String name : names) {
        lines.add(statusOf(name, ip));
      }
      Tasks.sync(this.plugin, () -> {
        this.plugin.messages().send(staff, "moderation.dupeip", Map.of(
            "ip", ip,
            "players", listed,
            "count", String.valueOf(names.size())
        ));
        for (int i = 0; i < names.size(); i++) {
          this.plugin.messages().send(staff, "moderation.dupeip-line", Map.of(
              "player", names.get(i),
              "status", lines.get(i)
          ));
        }
      });
    });
  }

  private String statusOf(String name, String ip) {
    Target resolved = resolve(name);
    java.util.List<String> flags = new java.util.ArrayList<>();
    flags.add(resolved.online != null
        ? this.plugin.messages().text("moderation.status-online")
        : this.plugin.messages().text("moderation.status-offline"));
    if (this.repository.active(PunishmentType.BAN, resolved.uuid, resolved.name, ip).isPresent()) {
      flags.add(this.plugin.messages().text("moderation.status-banned"));
    }
    if (this.repository.active(PunishmentType.IPBAN, resolved.uuid, resolved.name, ip).isPresent()) {
      flags.add(this.plugin.messages().text("moderation.status-ipban"));
    }
    if (this.repository.active(PunishmentType.MUTE, resolved.uuid, resolved.name, ip).isPresent()) {
      flags.add(this.plugin.messages().text("moderation.status-muted"));
    }
    int warns = this.repository.history(resolved.uuid, resolved.name, PunishmentType.WARN).size();
    if (warns > 0) {
      flags.add(this.plugin.messages().text("moderation.status-warns", Map.of("count", String.valueOf(warns))));
    }
    return String.join(" ", flags);
  }

  public Optional<Punishment> loginBlock(UUID uuid, String name, String ip) {
    Optional<Punishment> ban = this.repository.active(PunishmentType.BAN, uuid, name, ip);
    if (ban.isPresent()) {
      return ban;
    }
    return this.repository.active(PunishmentType.IPBAN, uuid, name, ip);
  }

  public Optional<Punishment> activeMute(Player player) {
    Punishment cached = this.mutes.get(player.getUniqueId());
    if (cached != null) {
      if (cached.expired()) {
        this.mutes.remove(player.getUniqueId());
        Tasks.async(this.plugin, () -> this.repository.deactivate(PunishmentType.MUTE, player.getUniqueId(), player.getName(), ip(player)));
        return Optional.empty();
      }
      return Optional.of(cached);
    }
    if (Bukkit.isPrimaryThread()) {
      return Optional.empty();
    }
    Optional<Punishment> found = this.repository.active(PunishmentType.MUTE, player.getUniqueId(), player.getName(), ip(player));
    found.ifPresent(mute -> this.mutes.put(player.getUniqueId(), mute));
    return found;
  }

  public void prefetchMute(UUID uuid, String name, String ip) {
    if (uuid != null && this.mutes.containsKey(uuid)) {
      return;
    }
    this.repository.active(PunishmentType.MUTE, uuid, name, ip)
        .ifPresent(mute -> this.mutes.put(uuid, mute));
  }

  public void forgetMute(UUID uuid) {
    this.mutes.remove(uuid);
  }

  public PunishmentRepository repository() {
    return this.repository;
  }

  public BanHoldService holds() {
    return this.holds;
  }

  private void disconnect(Player player, Punishment punishment) {
    if (this.holds.shouldHold(punishment)) {
      this.holds.start(player, punishment);
      return;
    }
    player.kick(DisconnectScreens.of(this.plugin, punishment));
  }

  private static Punishment pickWarn(List<Punishment> history, String token) {
    if (token == null || token.isBlank()) {
      return history.get(0);
    }
    try {
      long id = Long.parseLong(token);
      for (Punishment punishment : history) {
        if (punishment.id() == id) {
          return punishment;
        }
      }
    } catch (NumberFormatException ignored) {
      return null;
    }
    return null;
  }

  private void logAction(String action, String staff, String target, String reason, String extra) {
    if (this.plugin.webhooks() == null) {
      return;
    }
    String body = staff + " → " + target;
    if (reason != null && !reason.isBlank()) {
      body = body + "\n" + reason;
    }
    if (extra != null && !extra.isBlank()) {
      body = body + "\n" + extra;
    }
    this.plugin.webhooks().log("moderation", action, body);
  }

  private String reasonOrDefault(String reason, String configKey) {
    if (reason != null && !reason.isBlank()) {
      return reason.trim();
    }
    return this.plugin.configs().moderation().getString(configKey, this.plugin.messages().text("moderation.default-reason"));
  }

  private Long parseExpiry(String durationRaw) {
    if (durationRaw == null || durationRaw.isBlank()) {
      return null;
    }
    Long expiry = Durations.expiryFromNow(durationRaw);
    return expiry != null && expiry == Long.MIN_VALUE ? null : expiry;
  }

  private Target resolve(String name) {
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      return new Target(online.getUniqueId(), online.getName(), ip(online), online);
    }
    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
    if (cached != null && cached.getName() != null) {
      return new Target(cached.getUniqueId(), cached.getName(), "", null);
    }
    return new Target(null, name, "", null);
  }

  static String ip(Player player) {
    InetSocketAddress address = player.getAddress();
    if (address == null || address.getAddress() == null) {
      return "";
    }
    return address.getAddress().getHostAddress();
  }

  private static boolean looksLikeIp(String value) {
    return value != null && value.contains(".");
  }

  private record Target(UUID uuid, String name, String ip, Player online) {
  }
}
