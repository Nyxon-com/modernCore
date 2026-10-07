package com.valaryrpg.core.module.expeditions;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logica pura (testabile): token {rand:a-b}, tiro pesato, barra progresso.
 */
public final class ExpeditionRules {

    private static final Pattern RAND = Pattern.compile("\\{rand:(\\d+)-(\\d+)}");

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
}
