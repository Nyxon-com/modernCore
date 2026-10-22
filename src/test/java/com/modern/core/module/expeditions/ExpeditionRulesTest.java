//made with love by nyxon

package com.modern.core.module.expeditions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

  @Test
  void parseMmoGiveReadsTypeIdAmount() {
    assertArrayEquals(new String[]{"MATERIAL", "PERLA_TERRESTRE", "4"},
        ExpeditionRules.parseMmoGive("[console] mmoitems give MATERIAL PERLA_TERRESTRE {player} 4"));
    assertArrayEquals(new String[]{"GEM_STONE", "MAGMITE0", "1"},
        ExpeditionRules.parseMmoGive("mi give gem_stone magmite0 Steve"));
    assertNull(ExpeditionRules.parseMmoGive("[console] crate give {player} terratron 1"));
    assertNull(ExpeditionRules.parseMmoGive("[console] mmocore admin exp give {player} main 300"));
    assertEquals("crate give x 1", ExpeditionRules.stripPrefix("[CONSOLE]  crate give x 1"));
  }

  @Test
  void rewardSlotsAreCenteredInsideTheFrame() {
    assertEquals(List.of(), ExpeditionRules.rewardSlots(0));
    assertEquals(List.of(22), ExpeditionRules.rewardSlots(1));
    assertEquals(List.of(20, 22, 24), ExpeditionRules.rewardSlots(3));
    assertEquals(List.of(19, 20, 21, 23, 24, 25), ExpeditionRules.rewardSlots(6));
    assertEquals(List.of(10, 11, 12, 14, 15, 16, 20, 21, 22, 23, 24), ExpeditionRules.rewardSlots(11));
    assertEquals(21, ExpeditionRules.rewardSlots(40).size());
    for (int count = 1; count <= 21; count++) {
      List<Integer> slots = ExpeditionRules.rewardSlots(count);
      assertEquals(count, slots.size());
      assertEquals(count, slots.stream().distinct().count());
      for (int slot : slots) {
        int row = slot / 9;
        int column = slot % 9;
        assertTrue(row >= 1 && row <= 3 && column >= 1 && column <= 7, count + " -> " + slot);
      }
    }
  }
}
