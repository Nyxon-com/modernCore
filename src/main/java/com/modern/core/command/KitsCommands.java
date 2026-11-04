package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.kits.KitsModule;
import com.modern.core.util.OnlinePlayers;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KitsCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  KitsCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(kitCommand(), "Prendi un kit", this.settings.aliases("commands.kit-aliases"));
    registrar.register(kitsCommand(), "Apri la GUI kit", this.settings.aliases("commands.kits-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> kitCommand() {
    String name = this.settings.name("commands.kit", "kit");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "kit"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "kits.usage", Map.of("label", name));
            return 0;
          }
          module().kits().open(player);
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.argument("kit", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              String remaining = builder.getRemainingLowerCase();
              for (String kit : module().kits().names()) {
                if (kit.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                  builder.suggest(kit);
                }
              }
              return builder.buildFuture();
            })
            .executes(ctx -> claimSelf(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "kit")))
            .then(Commands.argument("player", StringArgumentType.word())
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "kit-others"))
                .suggests((ctx, builder) -> {
                  OnlinePlayers.suggest(builder);
                  return builder.buildFuture();
                })
                .executes(ctx -> claimOther(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "kit"),
                    StringArgumentType.getString(ctx, "player")
                ))))
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> kitsCommand() {
    String name = this.settings.name("commands.kits", "kits");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "kit"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
          }
          module().kits().open(player);
          return Command.SINGLE_SUCCESS;
        })
        .build();
  }

  private int claimSelf(CommandSender sender, String kit) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "kits.usage-console");
      return 0;
    }
    module().kits().claim(player, kit, false);
    return Command.SINGLE_SUCCESS;
  }

  private int claimOther(CommandSender sender, String kit, String playerName) {
    Player target = OnlinePlayers.exact(playerName);
    if (target == null) {
      this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", playerName));
      return 0;
    }
    module().kits().claim(sender, target, kit, Perms.has(this.plugin, sender, "kit-bypass"));
    return Command.SINGLE_SUCCESS;
  }

  private KitsModule module() {
    return this.plugin.modules().get("kits");
  }
}
