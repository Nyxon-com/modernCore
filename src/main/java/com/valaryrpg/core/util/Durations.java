package com.valaryrpg.core.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser durate CMI-like: 10s, 5m, 2h, 7d, 2w, 1mo, 1y. Vuoto/perm = permanente.
 */
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

    /**
     * @return epoch millis di scadenza, oppure null se permanente/invalido-trattato-come-permanente
     */
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

    public static String formatRemaining(Long expiresAt) {
        if (expiresAt == null) {
            return "permanente";
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            return "scaduto";
        }
        long days = TimeUnit.MILLISECONDS.toDays(remaining);
        long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
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
