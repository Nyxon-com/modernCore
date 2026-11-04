package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.smp.SmpModule;
import com.modern.core.module.smp.SmpText;
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
  }

  private SmpModule module() {
    return this.plugin.modules().get("smp");
  }
}
