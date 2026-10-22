//made with love by nyxon

package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.holograms.HologramsModule;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class HologramCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  HologramCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(command(), "Hologram configurabili", aliases("commands.holo-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> command() {
    String name = string("commands.holo", "vholo");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "holo"))
        .executes(ctx -> {
          this.plugin.messages().send(ctx.getSource().getSender(), "holograms.usage", Map.of("label", name));
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                    module().holograms().create(player, StringArgumentType.getString(ctx, "name"))))))
        .then(Commands.literal("delete")
            .then(named(ctx -> {
              module().holograms().delete(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
              return Command.SINGLE_SUCCESS;
            })))
        .then(Commands.literal("list")
            .executes(ctx -> {
              module().holograms().list(ctx.getSource().getSender());
              return Command.SINGLE_SUCCESS;
            }))
        .then(Commands.literal("info")
            .then(named(ctx -> {
              module().holograms().info(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
              return Command.SINGLE_SUCCESS;
            })))
        .then(Commands.literal("near").executes(ctx -> asPlayer(ctx.getSource().getSender(), player -> module().holograms().near(player))))
        .then(Commands.literal("movehere")
            .then(named(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                module().holograms().moveHere(player, StringArgumentType.getString(ctx, "name"))))))
        .then(Commands.literal("tp")
            .then(named(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                module().holograms().teleport(player, StringArgumentType.getString(ctx, "name"))))))
        .then(Commands.literal("enable")
            .then(named(ctx -> {
              module().holograms().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), true);
              return Command.SINGLE_SUCCESS;
            })))
        .then(Commands.literal("disable")
            .then(named(ctx -> {
              module().holograms().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), false);
              return Command.SINGLE_SUCCESS;
            })))
        .then(Commands.literal("reload")
            .executes(ctx -> {
              module().holograms().start();
              this.plugin.messages().send(ctx.getSource().getSender(), "holograms.reloaded");
              return Command.SINGLE_SUCCESS;
            }))
        .then(Commands.literal("import")
            .executes(ctx -> {
              module().holograms().importFancy(ctx.getSource().getSender(), "");
              return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("file", StringArgumentType.greedyString())
                .executes(ctx -> {
                  module().holograms().importFancy(
                      ctx.getSource().getSender(),
                      StringArgumentType.getString(ctx, "file")
                  );
                  return Command.SINGLE_SUCCESS;
                })))
        .then(Commands.literal("copy")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("target", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().copy(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "target")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("line")
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHolos(builder))
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                          module().holograms().addLine(
                              ctx.getSource().getSender(),
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "text")
                          );
                          return Command.SINGLE_SUCCESS;
                        }))))
            .then(Commands.literal("set")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHolos(builder))
                    .then(Commands.argument("index", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> {
                              module().holograms().setLine(
                                  ctx.getSource().getSender(),
                                  StringArgumentType.getString(ctx, "name"),
                                  StringArgumentType.getString(ctx, "index"),
                                  StringArgumentType.getString(ctx, "text")
                              );
                              return Command.SINGLE_SUCCESS;
                            })))))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHolos(builder))
                    .then(Commands.argument("index", StringArgumentType.word())
                        .executes(ctx -> {
                          module().holograms().removeLine(
                              ctx.getSource().getSender(),
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "index")
                          );
                          return Command.SINGLE_SUCCESS;
                        }))))
            .then(Commands.literal("insert")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHolos(builder))
                    .then(Commands.argument("index", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> {
                              module().holograms().insertLine(
                                  ctx.getSource().getSender(),
                                  StringArgumentType.getString(ctx, "name"),
                                  StringArgumentType.getString(ctx, "index"),
                                  StringArgumentType.getString(ctx, "text")
                              );
                              return Command.SINGLE_SUCCESS;
                            })))))
            .then(Commands.literal("clear")
                .then(named(ctx -> {
                  module().holograms().clearLines(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                  return Command.SINGLE_SUCCESS;
                }))))
        .then(Commands.literal("scale")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("x", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setScale(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "x"),
                          null,
                          null
                      );
                      return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("y", StringArgumentType.word())
                        .executes(ctx -> {
                          module().holograms().setScale(
                              ctx.getSource().getSender(),
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "x"),
                              StringArgumentType.getString(ctx, "y"),
                              null
                          );
                          return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("z", StringArgumentType.word())
                            .executes(ctx -> {
                              module().holograms().setScale(
                                  ctx.getSource().getSender(),
                                  StringArgumentType.getString(ctx, "name"),
                                  StringArgumentType.getString(ctx, "x"),
                                  StringArgumentType.getString(ctx, "y"),
                                  StringArgumentType.getString(ctx, "z")
                              );
                              return Command.SINGLE_SUCCESS;
                            }))))))
        .then(Commands.literal("billboard")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestKeys(builder, Set.of("fixed", "center", "horizontal", "vertical")))
                    .executes(ctx -> {
                      module().holograms().setBillboard(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("shadow")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("radius", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setShadow(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "radius"),
                          null
                      );
                      return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("strength", StringArgumentType.word())
                        .executes(ctx -> {
                          module().holograms().setShadow(
                              ctx.getSource().getSender(),
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "radius"),
                              StringArgumentType.getString(ctx, "strength")
                          );
                          return Command.SINGLE_SUCCESS;
                        })))))
        .then(Commands.literal("background")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.greedyString())
                    .executes(ctx -> {
                      module().holograms().setBackground(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("seethrough")
            .then(boolField((sender, holo, value) -> module().holograms().setSeeThrough(sender, holo, value))))
        .then(Commands.literal("textshadow")
            .then(boolField((sender, holo, value) -> module().holograms().setTextShadow(sender, holo, value))))
        .then(Commands.literal("alignment")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestKeys(builder, Set.of("left", "center", "right")))
                    .executes(ctx -> {
                      module().holograms().setAlignment(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("brightness")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("block", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setBrightness(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "block"),
                          null
                      );
                      return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("sky", StringArgumentType.word())
                        .executes(ctx -> {
                          module().holograms().setBrightness(
                              ctx.getSource().getSender(),
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "block"),
                              StringArgumentType.getString(ctx, "sky")
                          );
                          return Command.SINGLE_SUCCESS;
                        })))))
        .then(Commands.literal("distance")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setDistance(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("updateinterval")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setUpdateInterval(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .then(Commands.literal("yaw")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestHolos(builder))
                .then(Commands.argument("value", StringArgumentType.word())
                    .executes(ctx -> {
                      module().holograms().setYaw(
                          ctx.getSource().getSender(),
                          StringArgumentType.getString(ctx, "name"),
                          StringArgumentType.getString(ctx, "value")
                      );
                      return Command.SINGLE_SUCCESS;
                    }))))
        .build();
  }

  private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> named(
      com.mojang.brigadier.Command<CommandSourceStack> execute
  ) {
    return Commands.argument("name", StringArgumentType.word())
        .suggests((ctx, builder) -> suggestHolos(builder))
        .executes(execute);
  }

  private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> boolField(
      TriConsumer action
  ) {
    return Commands.argument("name", StringArgumentType.word())
        .suggests((ctx, builder) -> suggestHolos(builder))
        .then(Commands.argument("value", StringArgumentType.word())
            .suggests((ctx, builder) -> suggestKeys(builder, Set.of("on", "off")))
            .executes(ctx -> {
              action.accept(
                  ctx.getSource().getSender(),
                  StringArgumentType.getString(ctx, "name"),
                  StringArgumentType.getString(ctx, "value")
              );
              return Command.SINGLE_SUCCESS;
            }));
  }

  private int asPlayer(CommandSender sender, Consumer<Player> action) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    action.accept(player);
    return Command.SINGLE_SUCCESS;
  }

  private CompletableFuture<Suggestions> suggestHolos(SuggestionsBuilder builder) {
    return suggestKeys(builder, module().holograms().names());
  }

  private CompletableFuture<Suggestions> suggestKeys(SuggestionsBuilder builder, Set<String> keys) {
    String remaining = builder.getRemainingLowerCase();
    for (String key : keys) {
      if (key.toLowerCase(Locale.ROOT).startsWith(remaining)) {
        builder.suggest(key);
      }
    }
    return builder.buildFuture();
  }

  private HologramsModule module() {
    return this.plugin.modules().get("holograms");
  }

  private String string(String path, String fallback) {
    return this.settings.name(path, fallback);
  }

  private List<String> aliases(String path) {
    return this.settings.aliases(path);
  }

  @FunctionalInterface
  private interface TriConsumer {
    void accept(CommandSender sender, String name, String value);
  }
}
