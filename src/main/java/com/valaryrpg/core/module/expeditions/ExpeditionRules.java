package com.valaryrpg.core.module.expeditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logica pura (testabile): token {rand:a-b}, tiro pesato, barra progresso,
 * slot ricompense della GUI di riscatto, parsing "mmoitems give" per le icone.
 */
public final class ExpeditionRules {

    private static final Pattern RAND = Pattern.compile("\\{rand:(\\d+)-(\\d+)}");
    private static final Pattern PREFIX = Pattern.compile("^\\[(console|player)]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MMO_GIVE = Pattern.compile("^/?(?:mmoitems|mi)\\s+give\\s+(\\S+)\\s+(\\S+)\\s+\\S+(?:\\s+(\\d+))?.*$", Pattern.CASE_INSENSITIVE);

    private ExpeditionRules() {
    }

    /** Sostituisce ogni {rand:a-b} con un intero casuale in [a, b] (estremi inclusi). */
    public static String fillRandom(String line, Random random) {
        if (line == null || !line.contains("{rand:")) {
            return line;
        }
        Matcher matcher = RAND.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            long a = Long.parseLong(matcher.group(1));
            long b = Long.parseLong(matcher.group(2));
            long min = Math.min(a, b);
            long max = Math.max(a, b);
            long value = min + (long) Math.floor(random.nextDouble() * (max - min + 1));
            matcher.appendReplacement(out, String.valueOf(Math.min(value, max)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Indice estratto in base ai pesi (pesi <= 0 ignorati); -1 se nessun peso valido. */
    public static int pickWeighted(List<Integer> weights, Random random) {
        long total = 0;
        for (Integer weight : weights) {
            if (weight != null && weight > 0) {
                total += weight;
            }
        }
        if (total <= 0) {
            return -1;
        }
        long roll = (long) Math.floor(random.nextDouble() * total);
        long cursor = 0;
        for (int i = 0; i < weights.size(); i++) {
            Integer weight = weights.get(i);
            if (weight == null || weight <= 0) {
                continue;
            }
            cursor += weight;
            if (roll < cursor) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    /** Percentuale 0-100 di avanzamento. */
    public static int percent(long startedAt, long endsAt, long now) {
        if (endsAt <= startedAt || now >= endsAt) {
            return 100;
        }
        if (now <= startedAt) {
            return 0;
        }
        return (int) Math.min(100, Math.max(0, ((now - startedAt) * 100L) / (endsAt - startedAt)));
    }

    /** Barra MiniMessage a 20 segmenti. */
    public static String bar(int percent) {
        int filled = Math.max(0, Math.min(20, percent / 5));
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
    }

    /** Toglie il prefisso [console]/[player] da una riga azione. */
    public static String stripPrefix(String line) {
        return line == null ? "" : PREFIX.matcher(line.trim()).replaceFirst("").trim();
    }

    /**
     * "mmoitems give TYPE ID player [amount]" -> {TYPE, ID, amount}; null se la riga non è un give MMOItems.
     * La quantità manca = 1; va già risolto {rand:a-b}.
     */
    public static String[] parseMmoGive(String line) {
        Matcher matcher = MMO_GIVE.matcher(stripPrefix(line));
        if (!matcher.matches()) {
            return null;
        }
        String amount = matcher.group(3) == null ? "1" : matcher.group(3);
        return new String[]{matcher.group(1).toUpperCase(Locale.ROOT), matcher.group(2).toUpperCase(Locale.ROOT), amount};
    }

    /** Colonne (1-7) centrate per k elementi su una riga interna da 7. */
    static int[] columns(int k) {
        return switch (k) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            default -> new int[]{1, 2, 3, 4, 5, 6, 7};
        };
    }

    /**
     * Slot centrati per {@code count} ricompense in una GUI da 5 righe con cornice:
     * righe interne 1-3, 7 colonne ciascuna, blocco centrato in verticale. Max 21.
     */
    public static List<Integer> rewardSlots(int count) {
        int total = Math.max(0, Math.min(21, count));
        List<Integer> slots = new ArrayList<>(total);
        if (total == 0) {
            return slots;
        }
        int lines = (total + 6) / 7;
        int firstRow = 1 + (3 - lines) / 2;
        int left = total;
        for (int line = 0; line < lines; line++) {
            int remainingLines = lines - line;
            int inRow = (left + remainingLines - 1) / remainingLines;
            for (int column : columns(inRow)) {
                slots.add((firstRow + line) * 9 + column);
            }
            left -= inRow;
        }
        return slots;
    }
}
