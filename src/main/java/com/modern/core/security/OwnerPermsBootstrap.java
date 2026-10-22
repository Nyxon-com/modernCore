//made with love by nyxon

package com.modern.core.security;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OwnerPermsBootstrap {

  private final ModernCorePlugin plugin;

  public OwnerPermsBootstrap(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void schedule() {
    FileConfiguration root = this.plugin.configs().root();
    Path flag = this.plugin.getDataFolder().toPath().resolve("apply-owner-perms.flag");
    boolean configured = root.getBoolean("security.apply-owner-perms", true);
    boolean forced = Files.isRegularFile(flag);
    if (!configured && !forced) {
      return;
    }
    long delay = Math.max(20L, root.getLong("security.apply-owner-perms-delay-ticks", 60L));
    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      apply();
      if (forced) {
        try {
          Files.deleteIfExists(flag);
        } catch (IOException ignored) {

        }
      }
    }, delay);
  }

  public void apply() {
    OpFullAccessService ops = this.plugin.opFullAccess();
    if (ops == null) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.isOp()) {
        ops.refresh(player);
      }
    }
  }
}
