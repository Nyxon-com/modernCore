package com.valaryrpg.core.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Espande chiavi slot CommandPanels-like: "11", "45-52", "0,8,17".
 */
public final class SlotSpec {

    private SlotSpec() {
    }

    public static boolean contains(String spec, int slot) {
        return expand(spec).contains(slot);
    }

    public static List<Integer> expand(String spec) {
        List<Integer> slots = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return slots;
        }
        for (String part : spec.split(",")) {
            String token = part.trim();
            int dash = token.indexOf('-');
            if (dash > 0) {
                int from = parse(token.substring(0, dash), -1);
                int to = parse(token.substring(dash + 1), -1);
                if (from < 0 || to < 0) {
                    continue;
                }
                int min = Math.min(from, to);
                int max = Math.max(from, to);
                for (int i = min; i <= max; i++) {
                    slots.add(i);
                }
            } else {
                int value = parse(token, -1);
                if (value >= 0) {
                    slots.add(value);
                }
            }
        }
        return slots;
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
