package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.expeditions.ExpeditionsModule;
import com.modern.core.util.OnlinePlayers;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

final class ExpeditionsCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  ExpeditionsCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(command(), "Spedizioni VIP", this.settings.aliases("commands.expeditions-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> command() {
    String name = this.settings.name("commands.expeditions", "spedizioni");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "expeditions"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
          }
          ExpeditionsModule module = module();
          if (module == null) {
            this.plugin.messages().send(sender, "general.module-disabled", Map.of("module", "expeditions"));
            return 0;
          }
          module.open(player);
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.literal("completa")
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), "expeditions-admin"))
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  OnlinePlayers.suggest(builder);
                  return builder.buildFuture();
                })
                .executes(ctx -> {
                  CommandSender sender = ctx.getSource().getSender();
                  String targetName = StringArgumentType.getString(ctx, "player");
                  ExpeditionsModule module = module();
                  if (module == null) {
                    this.plugin.messages().send(sender, "general.module-disabled", Map.of("module", "expeditions"));
                    return 0;
                  }
                  OfflinePlayer target = Bukkit.getPlayerExact(targetName);
                  if (target == null) {
                    target = Bukkit.getOfflinePlayerIfCached(targetName);
                  }
                  if (target == null) {
                    this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", targetName));
                    return 0;
                  }
                  module.finishNow(sender, target);
                  return Command.SINGLE_SUCCESS;
                })))
        .build();
  }

  private ExpeditionsModule module() {
    return this.plugin.modules() == null || !this.plugin.modules().activeIds().contains("expeditions")
        ? null
        : this.plugin.modules().get("expeditions");
  }
}
