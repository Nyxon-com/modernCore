//made with love by nyxon

package com.modern.core.module.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class SmpText {
  public static final String BLUE = "#1A6EFF";
  public static final String CELESTE = "#6EEBFF";
  public static final String BRAND = "<b><gradient:" + BLUE + ":" + CELESTE + ">modernsmp</gradient></b>";
  public static final String PREFIX = BRAND + "<dark_gray> » </dark_gray>";

  private SmpText() {
  }

  public static void send(CommandSender to, String text) {
    Component body = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    to.sendMessage(MiniMessage.miniMessage().deserialize(PREFIX).append(body));
  }

  public static Component gradient(String text) {
    return MiniMessage.miniMessage().deserialize("<b><gradient:" + BLUE + ":" + CELESTE + ">" + text + "</gradient></b>");
  }

  public static Component parse(String raw) {
    if (raw != null && raw.indexOf('<') >= 0 && raw.indexOf('>') > raw.indexOf('<')) {
      return MiniMessage.miniMessage().deserialize(raw);
    }
    return LegacyComponentSerializer.legacyAmpersand().deserialize(raw == null ? "" : raw);
  }

  public static String money(double amount) {
    if (amount == Math.rint(amount)) {
      return String.valueOf((long) amount);
    }
    return String.format(Locale.US, "%.2f", amount);
  }

  public static String pretty(Material material) {
    String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (!out.isEmpty()) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return out.toString();
  }

  public static String timeLeft(long expires) {
    long left = Math.max(0L, expires - System.currentTimeMillis());
    long minutes = left / 60000L;
    long hours = minutes / 60L;
    long days = hours / 24L;
    if (days > 0) {
      return days + "g " + (hours % 24) + "h";
    }
    if (hours > 0) {
      return hours + "h " + (minutes % 60) + "m";
    }
    return Math.max(1L, minutes) + "m";
  }
}
