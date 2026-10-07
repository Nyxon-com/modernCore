package com.valaryrpg.core.module.expeditions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionRulesTest {

    @Test
    void fillRandomStaysInRange() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            String out = ExpeditionRules.fillRandom("give {player} X {rand:3-5}", random);
            int value = Integer.parseInt(out.substring(out.lastIndexOf(' ') + 1));
            assertTrue(value >= 3 && value <= 5, out);
            assertTrue(out.startsWith("give {player} X "));
        }
    }

    @Test
    void fillRandomHandlesMultipleAndSwappedBounds() {
        String out = ExpeditionRules.fillRandom("{rand:2-2} {rand:9-7}", new Random(1));
        String[] parts = out.split(" ");
        assertEquals("2", parts[0]);
        int second = Integer.parseInt(parts[1]);
        assertTrue(second >= 7 && second <= 9);
        assertEquals("no tokens", ExpeditionRules.fillRandom("no tokens", new Random()));
    }

    @Test
    void weightedPickRespectsZeroWeights() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int index = ExpeditionRules.pickWeighted(List.of(0, 10, 0), random);
            assertEquals(1, index);
        }
        assertEquals(-1, ExpeditionRules.pickWeighted(List.of(0, 0), random));
        assertEquals(-1, ExpeditionRules.pickWeighted(List.of(), random));
    }

    @Test
    void weightedPickDistribution() {
        Random random = new Random(123);
        int[] counts = new int[2];
        for (int i = 0; i < 10_000; i++) {
            counts[ExpeditionRules.pickWeighted(List.of(90, 10), random)]++;
        }
        assertTrue(counts[0] > 8500 && counts[0] < 9500, "counts " + counts[0]);
    }

    @Test
    void percentAndBar() {
        assertEquals(0, ExpeditionRules.percent(1000, 2000, 500));
        assertEquals(50, ExpeditionRules.percent(1000, 2000, 1500));
        assertEquals(100, ExpeditionRules.percent(1000, 2000, 2500));
        String bar = ExpeditionRules.bar(50);
        assertEquals(20, bar.chars().filter(c -> c == '|').count());
        assertFalse(bar.isEmpty());
    }
}
