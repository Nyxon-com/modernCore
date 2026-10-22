//made with love by nyxon

package com.modern.core.module.expeditions;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

final class ClaimSession {

  record Reward(ItemStack icon, List<String> commands, String label, boolean rare, boolean bonus) {
  }

  final UUID uuid;
  final ExpeditionRepository.Run run;
  final List<Reward> rewards;
  Inventory inventory;
  BukkitTask task;
  int revealed;
  int phase;
  boolean finished;
  boolean committing;
  boolean granted;

  ClaimSession(UUID uuid, ExpeditionRepository.Run run, List<Reward> rewards) {
    this.uuid = uuid;
    this.run = run;
    this.rewards = List.copyOf(rewards);
  }

  void cancelTask() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
  }
}
