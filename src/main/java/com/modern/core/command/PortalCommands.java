//made with love by nyxon

package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.mechanics.MechanicsModule;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PortalCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  PortalCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(portalCommand(), "Gestione portali", aliases("commands.portal-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> portalCommand() {
    String name = string("commands.portal", "portal");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "portal"))
        .executes(ctx -> {
          this.plugin.messages().send(ctx.getSource().getSender(), "portals.usage", Map.of("label", name));
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.literal("pos1").executes(ctx -> portalPlayer(ctx.getSource().getSender(), player -> module().portals().markPos1(player))))
        .then(Commands.literal("pos2").executes(ctx -> portalPlayer(ctx.getSource().getSender(), player -> module().portals().markPos2(player))))
        .then(Commands.literal("dest").executes(ctx -> portalPlayer(ctx.getSource().getSender(), player -> module().portals().markDest(player))))
        .then(Commands.literal("wand").executes(ctx -> portalPlayer(ctx.getSource().getSender(), player -> module().portals().giveWand(player))))
        .then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> portalPlayer(ctx.getSource().getSender(), player ->
                    module().portals().create(player, StringArgumentType.getString(ctx, "name"))))))
        .then(Commands.literal("item")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> portalPlayer(ctx.getSource().getSender(), player ->
                    module().portals().setItem(player, StringArgumentType.getString(ctx, "name"))))))
        .then(Commands.literal("enable")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> {
                  module().portals().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), true);
                  return Command.SINGLE_SUCCESS;
                })))
        .then(Commands.literal("disable")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> {
                  module().portals().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), false);
                  return Command.SINGLE_SUCCESS;
                })))
        .then(Commands.literal("delete")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> {
                  module().portals().delete(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                  return Command.SINGLE_SUCCESS;
                })))
        .then(Commands.literal("list")
            .executes(ctx -> {
              module().portals().list(ctx.getSource().getSender());
              return Command.SINGLE_SUCCESS;
            }))
        .then(Commands.literal("command")
            .executes(ctx -> {
              this.plugin.messages().send(ctx.getSource().getSender(), "portals.command-usage", Map.of("label", name));
              return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> {
                  this.plugin.messages().send(ctx.getSource().getSender(), "portals.command-usage", Map.of("label", name));
                  return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      module().portals().setCommand(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "command")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("clearcommands")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestPortals(builder))
                .executes(ctx -> {
                  module().portals().clearCommands(
                      ctx.getSource().getSender(),
                      StringArgumentType.getString(ctx, "name")
                  );
                  return Command.SINGLE_SUCCESS;
                })))
        .build();
  }

  private int portalPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    action.accept(player);
    return Command.SINGLE_SUCCESS;
  }

  private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPortals(
      com.mojang.brigadier.suggestion.SuggestionsBuilder builder
  ) {
    String remaining = builder.getRemainingLowerCase();
    for (String portal : module().portals().names()) {
      if (portal.toLowerCase(Locale.ROOT).startsWith(remaining)) {
        builder.suggest(portal);
      }
    }
    return builder.buildFuture();
  }

  private MechanicsModule module() {
    return this.plugin.modules().get("mechanics");
  }

  private String string(String path, String fallback) {
    return this.settings.name(path, fallback);
  }

  private List<String> aliases(String path) {
    return this.settings.aliases(path);
  }
}
