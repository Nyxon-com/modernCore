package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.util.Durations;
import com.valaryrpg.core.util.Tasks;
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
import java.util.stream.Collectors;

/**
 * Kick/ban/mute/warn/ipban persistiti su SQL. I mute restano in cache per non interrogare il DB a ogni chat.
 */
public final class ModerationService {

    private final ValaryCorePlugin plugin;
    private final PunishmentRepository repository;
    private final PlayerDataRepository players;
    private final BanHoldService holds;
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public ModerationService(
            ValaryCorePlugin plugin,
            PunishmentRepository repository,
            PlayerDataRepository players,
            BanHoldService holds
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.players = players;
        this.holds = holds;
    }

    public void kick(CommandSender staff, Player target, String reason) {
        String finalReason = reasonOrDefault(reason, "kick.default-reason");
        this.repository.insert(PunishmentType.KICK, target.getUniqueId(), target.getName(), ip(target), staff.getName(), finalReason, null);
        target.kick(DisconnectScreens.kick(this.plugin, target.getName(), staff.getName(), finalReason));
        this.plugin.messages().send(staff, "moderation.kick-staff", Map.of(
                "player", target.getName(),
                "reason", this.plugin.messages().escape(finalReason)
        ));
    }

    public void ban(CommandSender staff, String targetName, String durationRaw, String reason) {
        Target target = resolve(targetName);
        String finalReason = reasonOrDefault(reason, "ban.default-reason");
        Long expiry = parseExpiry(durationRaw);
        this.repository.insert(PunishmentType.BAN, target.uuid, target.name, target.ip, staff.getName(), finalReason, expiry);
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
                    true
            ));
        }
        this.plugin.messages().send(staff, "moderation.ban-staff", Map.of(
                "player", target.name,
                "reason", this.plugin.messages().escape(finalReason),
                "duration", Durations.formatRemaining(this.plugin, expiry)
        ));
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
        this.repository.insert(PunishmentType.IPBAN, uuid, name, ip, staff.getName(), finalReason, expiry);
        String bannedIp = ip;
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
                        true
                ));
            }
        }
        this.plugin.messages().send(staff, "moderation.ipban-staff", Map.of(
                "target", name,
                "ip", ip,
                "reason", this.plugin.messages().escape(finalReason),
                "duration", Durations.formatRemaining(this.plugin, expiry)
        ));
    }

    public void unban(CommandSender staff, String target) {
        Target resolved = looksLikeIp(target) ? new Target(null, target, target, null) : resolve(target);
        String ip = resolved.ip;
        if (ip.isBlank() && resolved.uuid != null) {
            ip = this.players.lastIp(resolved.uuid);
        }
        int bans = this.repository.deactivate(PunishmentType.BAN, resolved.uuid, resolved.name, ip);
        int ips = this.repository.deactivate(PunishmentType.IPBAN, resolved.uuid, resolved.name, ip);
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
                    "ip", ip.isBlank() ? target : ip
            ));
            return;
        }
        this.plugin.messages().send(staff, "moderation.unban-staff", Map.of("player", resolved.name));
    }

    public void history(CommandSender staff, String targetName) {
        Target target = looksLikeIp(targetName) ? new Target(null, targetName, targetName, null) : resolve(targetName);
        Tasks.async(this.plugin, () -> {
            String ip = target.ip;
            if (ip.isBlank() && target.uuid != null) {
                ip = this.players.lastIp(target.uuid);
            }
            List<Punishment> rows = this.repository.history(target.uuid, target.name, ip);
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
                for (int i = 0; i < shown.size(); i++) {
                    Punishment row = shown.get(i);
                    this.plugin.messages().send(staff, "moderation.history-line", Map.of(
                            "type", this.plugin.messages().text("moderation.history-type-" + row.type().name()),
                            "state", row.active() && !row.expired()
                                    ? this.plugin.messages().text("moderation.history-active")
                                    : this.plugin.messages().text("moderation.history-inactive"),
                            "reason", this.plugin.messages().escape(row.reason()),
                            "staff", this.plugin.messages().escape(row.staff()),
                            "duration", Durations.formatRemaining(this.plugin, row.expiresAt()),
                            "ip", row.targetIp() == null || row.targetIp().isBlank() ? "-" : row.targetIp()
                    ));
                }
            });
        });
    }

    public void mute(CommandSender staff, String targetName, String durationRaw, String reason) {
        Target target = resolve(targetName);
        String finalReason = reasonOrDefault(reason, "mute.default-reason");
        Long expiry = parseExpiry(durationRaw);
        Punishment mute = new Punishment(-1L, PunishmentType.MUTE, target.uuid, target.name, target.ip, staff.getName(), finalReason, System.currentTimeMillis(), expiry, true);
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
            });
        });
    }

    public void warn(CommandSender staff, String targetName, String reason) {
        Target target = resolve(targetName);
        String finalReason = reasonOrDefault(reason, "warn.default-reason");
        this.repository.insert(PunishmentType.WARN, target.uuid, target.name, target.ip, staff.getName(), finalReason, null);
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
    }

    public void warnings(CommandSender staff, String targetName) {
        Target target = resolve(targetName);
        List<Punishment> history = this.repository.history(target.uuid, target.name, PunishmentType.WARN);
        if (history.isEmpty()) {
            this.plugin.messages().send(staff, "moderation.warnings-empty", Map.of("player", target.name));
            return;
        }
        this.plugin.messages().send(staff, "moderation.warnings-header", Map.of(
                "player", target.name,
                "count", String.valueOf(history.size())
        ));
        int shown = Math.min(history.size(), this.plugin.configs().moderation().getInt("warn.max-listed", 10));
        for (int i = 0; i < shown; i++) {
            Punishment punishment = history.get(i);
            this.plugin.messages().send(staff, "moderation.warnings-line", Map.of(
                    "reason", this.plugin.messages().escape(punishment.reason()),
                    "staff", this.plugin.messages().escape(punishment.staff())
            ));
        }
    }

    public void banList(CommandSender staff) {
        List<Punishment> bans = this.repository.listActive(PunishmentType.BAN);
        List<Punishment> ips = this.repository.listActive(PunishmentType.IPBAN);
        if (bans.isEmpty() && ips.isEmpty()) {
            this.plugin.messages().send(staff, "moderation.banlist-empty");
            return;
        }
        String names = bans.stream().map(Punishment::targetName).collect(Collectors.joining(", "));
        String ipList = ips.stream().map(punishment -> punishment.targetIp() + "/" + punishment.targetName()).collect(Collectors.joining(", "));
        this.plugin.messages().send(staff, "moderation.banlist", Map.of(
                "bans", names.isBlank() ? "-" : names,
                "ips", ipList.isBlank() ? "-" : ipList
        ));
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
