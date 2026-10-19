package com.modern.core.module.vip;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VipXpBonusTest {

    private static Map<String, Double> ranks() {
        Map<String, Double> ranks = new LinkedHashMap<>();
        ranks.put("kryndel", 5D);
        ranks.put("quirith", 25D);
        ranks.put("iskara", 50D);
        return ranks;
    }

    @Test
    void highestOwnedRankWins() {
        Set<String> owned = Set.of("kryndel", "quirith");
        assertEquals(25D, VipXpBonus.bestPercent(ranks(), owned::contains));
        assertEquals(0D, VipXpBonus.bestPercent(ranks(), rank -> false));
        assertEquals(50D, VipXpBonus.bestPercent(ranks(), rank -> true));
    }

    @Test
    void applyMultipliesOnlyPositiveExperience() {
        assertEquals(150D, VipXpBonus.apply(100D, 50D), 1e-9);
        assertEquals(105D, VipXpBonus.apply(100D, 5D), 1e-9);
        assertEquals(-10D, VipXpBonus.apply(-10D, 50D));
        assertEquals(0D, VipXpBonus.apply(0D, 50D));
        assertEquals(100D, VipXpBonus.apply(100D, 0D));
    }
}
