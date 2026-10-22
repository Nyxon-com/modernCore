//made with love by nyxon

package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.mechanics.MechanicsModule;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LimitYCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  LimitYCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(limitYCommand(), "Limite Y per mondo", aliases("commands.limity-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> limitYCommand() {
    String name = string("commands.limity", "limity");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "limity"))
        .executes(ctx -> showLimit(ctx.getSource().getSender(), null))
        .then(Commands.literal("command")
            .executes(ctx -> {
              this.plugin.messages().send(ctx.getSource().getSender(), "limity.command-usage");
              return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("line", StringArgumentType.greedyString())
                .executes(ctx -> setLimitCommand(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "line")
                ))))
        .then(Commands.literal("notify")
            .executes(ctx -> {
              this.plugin.messages().send(ctx.getSource().getSender(), "limity.notify-usage");
              return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("on")
                .executes(ctx -> setNotify(ctx.getSource().getSender(), true, null))
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestWorlds(builder))
                    .executes(ctx -> setNotify(
                        ctx.getSource().getSender(),
                        true,
                        StringArgumentType.getString(ctx, "world")
                    ))))
            .then(Commands.literal("off")
                .executes(ctx -> setNotify(ctx.getSource().getSender(), false, null))
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestWorlds(builder))
                    .executes(ctx -> setNotify(
                        ctx.getSource().getSender(),
                        false,
                        StringArgumentType.getString(ctx, "world")
                    )))))
        .then(Commands.literal("clearcommand")
            .executes(ctx -> clearLimitCommand(ctx.getSource().getSender(), null))
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestWorlds(builder))
                .executes(ctx -> clearLimitCommand(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "world")
                ))))
        .then(Commands.literal("remove")
            .executes(ctx -> removeLimit(ctx.getSource().getSender(), null))
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestWorlds(builder))
                .executes(ctx -> removeLimit(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "world")
                ))))
        .then(Commands.literal("clear")
            .executes(ctx -> removeLimit(ctx.getSource().getSender(), null))
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestWorlds(builder))
                .executes(ctx -> removeLimit(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "world")
                ))))
        .then(Commands.argument("min", DoubleArgumentType.doubleArg())
            .then(Commands.argument("max", DoubleArgumentType.doubleArg())
                .executes(ctx -> setLimit(
                    ctx.getSource().getSender(),
                    DoubleArgumentType.getDouble(ctx, "min"),
                    DoubleArgumentType.getDouble(ctx, "max"),
                    null
                ))
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      for (World world : Bukkit.getWorlds()) {
                        if (world.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                          builder.suggest(world.getName());
                        }
                      }
                      return builder.buildFuture();
                    })
                    .executes(ctx -> setLimit(
                        ctx.getSource().getSender(),
                        DoubleArgumentType.getDouble(ctx, "min"),
                        DoubleArgumentType.getDouble(ctx, "max"),
                        StringArgumentType.getString(ctx, "world")
                    )))))
        .build();
  }

  private int showLimit(CommandSender sender, String worldName) {
    World world = worldOf(sender, worldName);
    if (world == null) {
      return 0;
    }
    String range = module().limitY().describe(world);
    if (range.isBlank()) {
      this.plugin.messages().send(sender, "limity.none", Map.of("world", world.getName()));
    } else {
      this.plugin.messages().send(sender, "limity.current", Map.of(
          "world", world.getName(),
          "range", range
      ));
    }
    String commands = module().limitY().commandsOf(world);
    this.plugin.messages().send(sender, commands.isBlank() ? "limity.commands-none" : "limity.commands", Map.of(
        "world", world.getName(),
        "commands", commands.isBlank() ? "-" : commands
    ));
    return Command.SINGLE_SUCCESS;
  }

  private int setLimit(CommandSender sender, double min, double max, String worldName) {
    World world;
    if (sender instanceof Player player) {
      world = player.getWorld();
      if (world == null) {
        this.plugin.messages().send(sender, "limity.usage");
        return 0;
      }
    } else {
      world = worldOf(sender, worldName);
      if (world == null) {
        return 0;
      }
    }
    double low = Math.min(min, max);
    double high = Math.max(min, max);
    module().limitY().set(world, low, high);
    this.plugin.messages().send(sender, "limity.set", Map.of(
        "world", world.getName(),
        "min", String.valueOf(low),
        "max", String.valueOf(high)
    ));
    return Command.SINGLE_SUCCESS;
  }

  private int setLimitCommand(CommandSender sender, String rest) {
    String trimmed = rest == null ? "" : rest.trim();
    if (trimmed.isBlank()) {
      this.plugin.messages().send(sender, "limity.command-usage");
      return 0;
    }
    World world = worldOf(sender, null);
    if (world == null) {
      return 0;
    }
    module().limitY().setCommand(world, trimmed);
    this.plugin.messages().send(sender, "limity.command-set", Map.of(
        "world", world.getName(),
        "command", trimmed
    ));
    return Command.SINGLE_SUCCESS;
  }

  private int clearLimitCommand(CommandSender sender, String worldName) {
    World world = worldOf(sender, worldName);
    if (world == null) {
      return 0;
    }
    module().limitY().clearCommands(world);
    this.plugin.messages().send(sender, "limity.command-cleared", Map.of("world", world.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private int removeLimit(CommandSender sender, String worldName) {
    String name = worldName;
    if (name == null || name.isBlank()) {
      if (!(sender instanceof Player player) || player.getWorld() == null) {
        this.plugin.messages().send(sender, "limity.usage");
        return 0;
      }
      name = player.getWorld().getName();
    }
    module().limitY().remove(name);
    this.plugin.messages().send(sender, "limity.removed", Map.of("world", name));
    return Command.SINGLE_SUCCESS;
  }

  private int setNotify(CommandSender sender, boolean enabled, String worldName) {
    World world = worldOf(sender, worldName);
    if (world == null) {
      return 0;
    }
    module().limitY().setNotify(world, enabled);
    this.plugin.messages().send(sender, enabled ? "limity.notify-on" : "limity.notify-off", Map.of("world", world.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWorlds(
      com.mojang.brigadier.suggestion.SuggestionsBuilder builder
  ) {
    String remaining = builder.getRemainingLowerCase();
    org.bukkit.configuration.ConfigurationSection worlds = this.plugin.configs().mechanics().getConfigurationSection("limity.worlds");
    if (worlds != null) {
      for (String name : worlds.getKeys(false)) {
        if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
          builder.suggest(name);
        }
      }
    }
    for (World world : Bukkit.getWorlds()) {
      if (world.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
        builder.suggest(world.getName());
      }
    }
    return builder.buildFuture();
  }

  private World worldOf(CommandSender sender, String worldName) {
    if (worldName == null || worldName.isBlank()) {
      if (!(sender instanceof Player player) || player.getWorld() == null) {
        this.plugin.messages().send(sender, "limity.usage");
        return null;
      }
      return player.getWorld();
    }
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      this.plugin.messages().send(sender, "general.invalid-world", Map.of("world", worldName));
    }
    return world;
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
