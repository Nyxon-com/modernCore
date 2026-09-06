package com.valaryrpg.core.module.moderation;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayerDataRepository;
import com.valaryrpg.core.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ModerationService {

    private final ValaryCorePlugin plugin;
    private final PunishmentRepository repository;
    private final PlayerDataRepository players;

    public ModerationService(ValaryCorePlugin plugin, PunishmentRepository repository, PlayerDataRepository players) {
        this.plugin = plugin;
        this.repository = repository;
        this.players = players;
    }

    public void kick(CommandSender staff, Player target, String reason) {
        String finalReason = reasonOrDefault(reason, "kick.default-reason");
        this.repository.insert(PunishmentType.KICK, target.getUniqueId(), target.getName(), ip(target), staff.getName(), finalReason, null);
        target.kick(this.plugin.messages().component("moderation.kick-screen", Map.of(
                "reason", this.plugin.messages().escape(finalReason),
                "staff", this.plugin.messages().escape(staff.getName())
        )));
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
            target.online.kick(this.plugin.messages().component("moderation.ban-screen", Map.of(
                    "reason", this.plugin.messages().escape(finalReason),
                    "staff", this.plugin.messages().escape(staff.getName()),
                    "duration", Durations.formatRemaining(expiry)
            )));
        }
        this.plugin.messages().send(staff, "moderation.ban-staff", Map.of(
                "player", target.name,
                "reason", this.plugin.messages().escape(finalReason),
                "duration", Durations.formatRemaining(expiry)
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
                player.kick(this.plugin.messages().component("moderation.ipban-screen", Map.of(
                        "reason", this.plugin.messages().escape(finalReason),
                        "duration", Durations.formatRemaining(expiry)
                )));
            }
        }
        this.plugin.messages().send(staff, "moderation.ipban-staff", Map.of(
                "target", name,
                "ip", ip,
                "reason", this.plugin.messages().escape(finalReason),
                "duration", Durations.formatRemaining(expiry)
        ));
    }

    public void unban(CommandSender staff, String target) {
        Target resolved = looksLikeIp(target) ? new Target(null, target, target, null) : resolve(target);
        int bans = this.repository.deactivate(PunishmentType.BAN, resolved.uuid, resolved.name, resolved.ip);
        int ips = this.repository.deactivate(PunishmentType.IPBAN, resolved.uuid, resolved.name, target);
        if (bans + ips <= 0) {
            this.plugin.messages().send(staff, "moderation.unban-missing", Map.of("player", target));
            return;
        }
        this.plugin.messages().send(staff, "moderation.unban-staff", Map.of("player", target));
    }

    public void mute(CommandSender staff, String targetName, String durationRaw, String reason) {
        Target target = resolve(targetName);
        String finalReason = reasonOrDefault(reason, "mute.default-reason");
        Long expiry = parseExpiry(durationRaw);
        this.repository.insert(PunishmentType.MUTE, target.uuid, target.name, target.ip, staff.getName(), finalReason, expiry);
        if (target.online != null) {
            this.plugin.messages().send(target.online, "moderation.mute-target", Map.of(
                    "reason", this.plugin.messages().escape(finalReason),
                    "duration", Durations.formatRemaining(expiry)
            ));
        }
        this.plugin.messages().send(staff, "moderation.mute-staff", Map.of(
                "player", target.name,
                "reason", this.plugin.messages().escape(finalReason),
                "duration", Durations.formatRemaining(expiry)
        ));
    }

    public void unmute(CommandSender staff, String targetName) {
        Target target = resolve(targetName);
        int updated = this.repository.deactivate(PunishmentType.MUTE, target.uuid, target.name, target.ip);
        if (updated <= 0) {
            this.plugin.messages().send(staff, "moderation.unmute-missing", Map.of("player", target.name));
            return;
        }
        this.plugin.messages().send(staff, "moderation.unmute-staff", Map.of("player", target.name));
    }

    public void warn(CommandSender staff, String targetName, String reason) {
        Target target = resolve(targetName);
        String finalReason = reasonOrDefault(reason, "warn.default-reason");
        this.repository.insert(PunishmentType.WARN, target.uuid, target.name, target.ip, staff.getName(), finalReason, System.currentTimeMillis());
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
            this.plugin.messages().send(staff, "moderation.dupeip-missing", Map.of("player", target));
            return;
        }
        java.util.List<String> names = this.players.namesByIp(ip);
        this.plugin.messages().send(staff, "moderation.dupeip", Map.of(
                "ip", ip,
                "players", names.isEmpty() ? "-" : String.join(", ", names)
        ));
    }

    public Optional<Punishment> loginBlock(UUID uuid, String name, String ip) {
        Optional<Punishment> ban = this.repository.active(PunishmentType.BAN, uuid, name, ip);
        if (ban.isPresent()) {
            return ban;
        }
        return this.repository.active(PunishmentType.IPBAN, uuid, name, ip);
    }

    public Optional<Punishment> activeMute(Player player) {
        return this.repository.active(PunishmentType.MUTE, player.getUniqueId(), player.getName(), ip(player));
    }

    public PunishmentRepository repository() {
        return this.repository;
    }

    private String reasonOrDefault(String reason, String configKey) {
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return this.plugin.configs().moderation().getString(configKey, "No reason");
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
