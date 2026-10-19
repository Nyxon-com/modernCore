package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * /triggerblock: blocco con hologram, click-comando, particelle e animazioni.
 */
final class HoloBlockCommands {

    private final ModernCorePlugin plugin;
    private final CommandSettings settings;

    HoloBlockCommands(ModernCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    void register(Commands registrar) {
        registrar.register(holoCommand(), "Trigger su blocco", aliases("commands.triggerblock-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> holoCommand() {
        String name = string("commands.triggerblock", "triggerblock");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "triggerblock"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "holos.usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("wand").executes(ctx -> asPlayer(ctx.getSource().getSender(), player -> module().holos().giveWand(player))))
                .then(Commands.literal("set").executes(ctx -> asPlayer(ctx.getSource().getSender(), player -> module().holos().markLooking(player))))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                                        module().holos().create(player, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), true);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("disable")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().enable(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), false);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().delete(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            module().holos().list(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().info(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("command")
                        .executes(ctx -> {
                            this.plugin.messages().send(ctx.getSource().getSender(), "holos.command-usage", Map.of("label", name));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    this.plugin.messages().send(ctx.getSource().getSender(), "holos.command-usage", Map.of("label", name));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            module().holos().setCommand(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "command")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("clearcommands")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().clearCommands(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "name")
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("line")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestHolos(builder))
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    module().holos().addLine(
                                                            ctx.getSource().getSender(),
                                                            StringArgumentType.getString(ctx, "name"),
                                                            StringArgumentType.getString(ctx, "text")
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                }))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestHolos(builder))
                                        .executes(ctx -> {
                                            module().holos().clearLines(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("animation")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestKeys(builder, module().holos().animationNames()))
                                        .executes(ctx -> {
                                            module().holos().setAnimation(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "preset")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("particle")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestKeys(builder, module().holos().particleNames()))
                                        .executes(ctx -> {
                                            module().holos().setParticle(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "preset")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("clickparticle")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestKeys(builder, module().holos().clickParticleNames()))
                                        .executes(ctx -> {
                                            module().holos().setClickParticle(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "preset")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("sound")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("sound", StringArgumentType.word())
                                        .executes(ctx -> {
                                            module().holos().setSound(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "sound")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("icon")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                                        module().holos().setIcon(player, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("glow")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestKeys(builder, Set.of("on", "off")))
                                        .executes(ctx -> {
                                            module().holos().setGlow(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "value")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("permission")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .executes(ctx -> {
                                    module().holos().setPermission(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "name"),
                                            ""
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("permission", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            module().holos().setPermission(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "permission")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("scale")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> {
                                            module().holos().setScale(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "value")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("offset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestHolos(builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> {
                                            module().holos().setOffset(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "value")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
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
        return suggestKeys(builder, module().holos().names(), false);
    }

    private CompletableFuture<Suggestions> suggestKeys(SuggestionsBuilder builder, Set<String> keys) {
        return suggestKeys(builder, keys, true);
    }

    private CompletableFuture<Suggestions> suggestKeys(SuggestionsBuilder builder, Set<String> keys, boolean includeNone) {
        String remaining = builder.getRemainingLowerCase();
        for (String key : keys) {
            if (key.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(key);
            }
        }
        if (includeNone && "none".startsWith(remaining)) {
            builder.suggest("none");
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
