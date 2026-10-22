//made with love by nyxon

package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Durations;

import java.util.HashMap;
import java.util.Map;

final class PunishmentFormat {

  private final ModernCorePlugin plugin;

  PunishmentFormat(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  Map<String, String> linePlaceholders(Punishment row) {
    Map<String, String> values = new HashMap<>();
    values.put("type", this.plugin.messages().text("moderation.history-type-" + row.type().name()));
    values.put("state", historyState(row));
    values.put("reason", this.plugin.messages().escape(row.reason()));
    values.put("staff", this.plugin.messages().escape(row.staff()));
    values.put("duration", historyDuration(row));
    values.put("when", Durations.formatWhen(this.plugin, row.createdAt()));
    values.put("ip", row.targetIp() == null || row.targetIp().isBlank() ? "-" : row.targetIp());
    values.put("player", row.targetName() == null ? "-" : row.targetName());
    values.put("revoke-reason", row.revokeReason() == null || row.revokeReason().isBlank()
        ? this.plugin.messages().text("moderation.default-reason")
        : this.plugin.messages().escape(row.revokeReason()));
    values.put("revoked-by", row.revokedBy() == null || row.revokedBy().isBlank() ? "-" : this.plugin.messages().escape(row.revokedBy()));
    values.put("revoked-when", row.revokedAt() == null ? "-" : Durations.formatWhen(this.plugin, row.revokedAt()));
    return values;
  }

  Map<String, String> banLine(Punishment row) {
    return Map.of(
        "player", row.targetName() == null || row.targetName().isBlank() ? "-" : row.targetName(),
        "ip", row.targetIp() == null || row.targetIp().isBlank() ? "-" : row.targetIp(),
        "reason", this.plugin.messages().escape(row.reason()),
        "staff", this.plugin.messages().escape(row.staff()),
        "when", Durations.formatWhen(this.plugin, row.createdAt()),
        "duration", Durations.formatRemaining(this.plugin, row.expiresAt())
    );
  }

  String historyLineKey(Punishment row) {
    if (row.type() == PunishmentType.KICK || row.type() == PunishmentType.WARN) {
      return "moderation.history-line";
    }
    if (row.revoked() || (!row.active() && !row.expired())) {
      return "moderation.history-line-revoked";
    }
    if (row.expired() || !row.active()) {
      return "moderation.history-line-expired";
    }
    return "moderation.history-line";
  }

  private String historyState(Punishment row) {
    if (row.type() == PunishmentType.KICK || row.type() == PunishmentType.WARN) {
      return this.plugin.messages().text("moderation.history-event");
    }
    if (row.revoked() || (!row.active() && !row.expired())) {
      return this.plugin.messages().text("moderation.history-inactive");
    }
    if (row.expired() || !row.active()) {
      return this.plugin.messages().text("moderation.history-expired");
    }
    if (row.expiresAt() == null) {
      return this.plugin.messages().text("moderation.history-permanent");
    }
    return this.plugin.messages().text("moderation.history-active");
  }

  private String historyDuration(Punishment row) {
    if (row.type() == PunishmentType.KICK || row.type() == PunishmentType.WARN) {
      return this.plugin.messages().text("duration.none");
    }
    if (row.expiresAt() == null) {
      return this.plugin.messages().text("duration.permanent");
    }
    return Durations.formatRemaining(this.plugin, row.expiresAt());
  }
}
