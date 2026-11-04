package com.modern.core.util;

import com.modern.core.ModernCorePlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

  private static final Pattern TOKEN = Pattern.compile("(\\d+)(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);

  private Durations() {
  }

  public static boolean looksLikeDuration(String raw) {
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (value.equals("perm") || value.equals("permanent") || value.equals("-1")) {
      return true;
    }
    Long expiry = expiryFromNow(raw);
    return expiry != null && expiry != Long.MIN_VALUE;
  }

  public static Long expiryFromNow(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (value.equals("perm") || value.equals("permanent") || value.equals("-1")) {
      return null;
    }
    Matcher matcher = TOKEN.matcher(value);
    long totalMs = 0L;
    int consumed = 0;
    while (matcher.find()) {
      consumed += matcher.group().length();
      long amount = Long.parseLong(matcher.group(1));
      totalMs += toMillis(amount, matcher.group(2).toLowerCase(Locale.ROOT));
    }
    if (totalMs <= 0 || consumed != value.length()) {
      return Long.MIN_VALUE;
    }
    return System.currentTimeMillis() + totalMs;
  }

  public static String formatRemaining(ModernCorePlugin plugin, Long expiresAt) {
    if (expiresAt == null) {
      return plugin.messages().text("duration.permanent");
    }
    long remaining = expiresAt - System.currentTimeMillis();
    if (remaining <= 0) {
      return plugin.messages().text("duration.expired");
    }
    long days = TimeUnit.MILLISECONDS.toDays(remaining);
    long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
    long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
    long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
    Map<String, String> parts = Map.of(
        "days", String.valueOf(days),
        "hours", String.valueOf(hours),
        "minutes", String.valueOf(minutes),
        "seconds", String.valueOf(seconds)
    );
    if (days > 0) {
      return plugin.messages().text("duration.days", parts);
    }
    if (hours > 0) {
      return plugin.messages().text("duration.hours", parts);
    }
    if (minutes > 0) {
      return plugin.messages().text("duration.minutes", parts);
    }
    return plugin.messages().text("duration.seconds", Map.of(
        "seconds", String.valueOf(Math.max(1, TimeUnit.MILLISECONDS.toSeconds(remaining)))
    ));
  }

  public static String formatWhen(ModernCorePlugin plugin, long epochMs) {
    String pattern = plugin.configs().messages().getString("duration.when", "dd/MM/yyyy HH:mm");
    try {
      return DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? "dd/MM/yyyy HH:mm" : pattern)
          .withZone(ZoneId.systemDefault())
          .format(Instant.ofEpochMilli(epochMs));
    } catch (RuntimeException exception) {
      return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
          .withZone(ZoneId.systemDefault())
          .format(Instant.ofEpochMilli(epochMs));
    }
  }

  private static long toMillis(long amount, String unit) {
    return switch (unit) {
      case "s" -> TimeUnit.SECONDS.toMillis(amount);
      case "m" -> TimeUnit.MINUTES.toMillis(amount);
      case "h" -> TimeUnit.HOURS.toMillis(amount);
      case "d" -> TimeUnit.DAYS.toMillis(amount);
      case "w" -> TimeUnit.DAYS.toMillis(amount * 7);
      case "mo" -> TimeUnit.DAYS.toMillis(amount * 30);
      case "y" -> TimeUnit.DAYS.toMillis(amount * 365);
      default -> 0L;
    };
  }
}
