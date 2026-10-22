//made with love by nyxon

package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Durations;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DisconnectScreens {

  private DisconnectScreens() {
  }

  public static Component kick(ModernCorePlugin plugin, String player, String staff, String reason) {
    return plugin.messages().component("moderation.kick-screen", values(plugin, player, staff, reason, null, ""));
  }

  public static Component ban(ModernCorePlugin plugin, String player, String staff, String reason, Long expiry) {
    return plugin.messages().component("moderation.ban-screen", values(plugin, player, staff, reason, expiry, ""));
  }

  public static Component ipBan(ModernCorePlugin plugin, String player, String staff, String reason, Long expiry, String ip) {
    return plugin.messages().component("moderation.ipban-screen", values(plugin, player, staff, reason, expiry, ip));
  }

  public static Component of(ModernCorePlugin plugin, Punishment punishment) {
    if (punishment.type() == PunishmentType.IPBAN) {
      return ipBan(plugin, punishment.targetName(), punishment.staff(), punishment.reason(), punishment.expiresAt(), punishment.targetIp());
    }
    return ban(plugin, punishment.targetName(), punishment.staff(), punishment.reason(), punishment.expiresAt());
  }

  public static Dialog live(ModernCorePlugin plugin, Punishment punishment) {
    Map<String, String> values = values(
        plugin,
        punishment.targetName(),
        punishment.staff(),
        punishment.reason(),
        punishment.expiresAt(),
        punishment.targetIp()
    );
    int width = Math.clamp(plugin.configs().moderation().getInt("screen.dialog-width", 320), 1, 1024);
    Key leave = leaveKey(plugin);
    return Dialog.create(builder -> builder.empty()
        .base(DialogBase.builder(plugin.messages().component("moderation.ban-dialog-title", values))
            .canCloseWithEscape(false)
            .body(List.of(DialogBody.plainMessage(plugin.messages().component("moderation.ban-dialog-body", values), width)))
            .build())
        .type(DialogType.notice(ActionButton.builder(plugin.messages().component("moderation.ban-dialog-leave", values))
            .action(DialogAction.customClick(leave, null))
            .build())));
  }

  public static boolean leaveClick(ModernCorePlugin plugin, Key identifier) {
    return identifier != null && identifier.equals(leaveKey(plugin));
  }

  private static Key leaveKey(ModernCorePlugin plugin) {
    String raw = plugin.configs().moderation().getString("screen.leave-click", "valaryrpg:ban-leave");
    try {
      return Key.key(raw == null || raw.isBlank() ? "valaryrpg:ban-leave" : raw);
    } catch (Exception exception) {
      return Key.key("valaryrpg", "ban-leave");
    }
  }

  private static Map<String, String> values(
      ModernCorePlugin plugin,
      String player,
      String staff,
      String reason,
      Long expiry,
      String ip
  ) {
    Map<String, String> values = new HashMap<>();
    values.put("player", plugin.messages().escape(safe(player)));
    values.put("staff", plugin.messages().escape(safe(staff)));
    values.put("reason", plugin.messages().escape(safe(reason)));
    values.put("duration", Durations.formatRemaining(plugin, expiry));
    values.put("ip", plugin.messages().escape(safe(ip)));
    values.put("line", plugin.messages().text("moderation.screen-rule"));
    values.put("appeal", plugin.messages().text("moderation.appeal"));
    values.put("ip-line", ip == null || ip.isBlank()
        ? ""
        : plugin.messages().text("moderation.ban-dialog-ip", Map.of("ip", plugin.messages().escape(ip))));
    return values;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
