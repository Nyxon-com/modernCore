package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.smp.SmpModule;
import com.modern.core.module.smp.SmpText;
import com.modern.core.module.smp.TeamCommand;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.List;

final class SmpCommands {
  private final ModernCorePlugin plugin;

  SmpCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  void register(Commands registrar) {
    registrar.register(Commands.literal("play")
        .requires(stack -> stack.getSender().hasPermission("modernsmp.play"))
        .executes(ctx -> {
          if (!(ctx.getSource().getSender() instanceof Player player)) {
            SmpText.send(ctx.getSource().getSender(), "&cSolo in gioco.");
            return 0;
          }
          module().context().worlds().play(player);
          return Command.SINGLE_SUCCESS;
        }).build(), "Vai nel mondo di gioco", List.of());
    registrar.register(Commands.literal("rtp")
        .requires(stack -> stack.getSender().hasPermission("modernsmp.rtp"))
        .executes(ctx -> {
          if (!(ctx.getSource().getSender() instanceof Player player)) {
            SmpText.send(ctx.getSource().getSender(), "&cSolo in gioco.");
            return 0;
          }
          module().context().worlds().rtp(player, null);
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.argument("mondo", StringArgumentType.word())
            .executes(ctx -> {
              if (!(ctx.getSource().getSender() instanceof Player player)) {
                SmpText.send(ctx.getSource().getSender(), "&cSolo in gioco.");
                return 0;
              }
              module().context().worlds().rtp(player, StringArgumentType.getString(ctx, "mondo"));
              return Command.SINGLE_SUCCESS;
            }))
        .build(), "Teletrasporto casuale nel mondo", List.of());
    registrar.register(Commands.literal("ah")
        .requires(stack -> stack.getSender().hasPermission("modernsmp.ah"))
        .then(Commands.literal("sell")
            .then(Commands.argument("prezzo", DoubleArgumentType.doubleArg(0))
                .executes(ctx -> {
                  if (!(ctx.getSource().getSender() instanceof Player player)) {
                    SmpText.send(ctx.getSource().getSender(), "&cSolo in gioco.");
                    return 0;
                  }
                  module().context().auctions().sell(player, DoubleArgumentType.getDouble(ctx, "prezzo"));
                  return Command.SINGLE_SUCCESS;
                })))
        .executes(ctx -> {
          if (!(ctx.getSource().getSender() instanceof Player player)) {
            SmpText.send(ctx.getSource().getSender(), "&cSolo in gioco.");
            return 0;
          }
          module().context().auctions().open(player);
          return Command.SINGLE_SUCCESS;
        }).build(), "Asta tra giocatori", List.of("auction", "asta"));
    registrar.register(Commands.literal("team")
        .requires(stack -> stack.getSender().hasPermission("modernsmp.team"))
        .then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(ctx -> team(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "args").split(" "))))
        .executes(ctx -> team(ctx.getSource().getSender(), new String[0]))
        .build(), "Team della SMP", List.of("teams"));
  }

  private int team(org.bukkit.command.CommandSender sender, String[] args) {
    new TeamCommand(module().context()).run(sender, args);
    return Command.SINGLE_SUCCESS;
  }

  private SmpModule module() {
    return this.plugin.modules().get("smp");
  }
}
