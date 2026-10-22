//made with love by nyxon

package com.modern.core.module.vip;

import java.util.Map;
import java.util.function.Predicate;

public final class VipXpBonus {

  private VipXpBonus() {
  }

  public static double bestPercent(Map<String, Double> ranks, Predicate<String> hasRank) {
    double best = 0;
    for (Map.Entry<String, Double> entry : ranks.entrySet()) {
      Double value = entry.getValue();
      if (value != null && value > best && hasRank.test(entry.getKey())) {
        best = value;
      }
    }
    return best;
  }

  public static double apply(double experience, double percent) {
    if (experience <= 0 || percent <= 0 || Double.isNaN(experience) || Double.isInfinite(experience)) {
      return experience;
    }
    return experience * (1.0D + percent / 100.0D);
  }
}
