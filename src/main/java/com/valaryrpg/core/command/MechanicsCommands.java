package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.mechanics.MechanicsModule;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MechanicsCommands {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;

    MechanicsCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    void register(Commands registrar) {
        registrar.register(limitYCommand(), "Limite Y per mondo", aliases("commands.limity-aliases"));
        registrar.register(checkDeathCommand(), "Ultima posizione di morte", aliases("commands.checkdeath-aliases"));
        registrar.register(copyCoordCommand(), "Copia le coordinate", aliases("commands.copycoord-aliases"));
        registrar.register(portalCommand(), "Gestione portali", aliases("commands.portal-aliases"));
        registrar.register(pressureJumpCommand(), "Long jump su pressure plate", aliases("commands.pressureplayerjump-aliases"));
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
                .then(Commands.literal("clearcommand")
                        .executes(ctx -> clearLimitCommand(ctx.getSource().getSender(), null))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestWorlds(builder))
                                .executes(ctx -> clearLimitCommand(
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

    private LiteralCommandNode<CommandSourceStack> checkDeathCommand() {
        String name = string("commands.checkdeath", "checkdeath");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "checkdeath"))
                .executes(ctx -> {
                    module().deaths().show(ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("restore")
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "checkdeath-restore"))
                        .executes(ctx -> {
                            module().deaths().restore(ctx.getSource().getSender(), null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "checkdeath-others"))
                                .suggests((ctx, builder) -> {
                                    OnlinePlayers.suggest(builder);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    module().deaths().restore(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "player")
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "checkdeath-others"))
                        .suggests((ctx, builder) -> {
                            OnlinePlayers.suggest(builder);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            module().deaths().show(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> copyCoordCommand() {
        String name = string("commands.copycoord", "copycoord");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "copycoord"))
                .executes(ctx -> {
                    module().deaths().copy(ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "copycoord-others"))
                        .suggests((ctx, builder) -> {
                            OnlinePlayers.suggest(builder);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            module().deaths().copy(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
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
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestPortals(builder))
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

    private LiteralCommandNode<CommandSourceStack> pressureJumpCommand() {
        String name = string("commands.pressureplayerjump", "pressureplayerjump");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "pressureplayerjump"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        this.plugin.messages().send(ctx.getSource().getSender(), "general.player-only");
                        return 0;
                    }
                    module().longJumps().info(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("set")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    org.bukkit.configuration.ConfigurationSection plates = this.plugin.configs().mechanics()
                                            .getConfigurationSection("long-jump.plates");
                                    if (plates != null) {
                                        for (String key : plates.getKeys(false)) {
                                            if (key.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                                builder.suggest(key);
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> portalPlayer(ctx.getSource().getSender(), player ->
                                        module().longJumps().bindLooking(player, StringArgumentType.getString(ctx, "preset"))))))
                .then(Commands.literal("remove")
                        .executes(ctx -> portalPlayer(ctx.getSource().getSender(), player -> module().longJumps().unbindLooking(player))))
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

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPortals(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String portal : module().portals().names()) {
            if (portal.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(portal);
            }
        }
        return builder.buildFuture();
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
        World world = worldOf(sender, worldName);
        if (world == null) {
            return 0;
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
        String[] parts = trimmed.split("\\s+", 2);
        World named = Bukkit.getWorld(parts[0]);
        World world;
        String command;
        if (named != null && parts.length >= 2) {
            world = named;
            command = parts[1];
        } else if (named != null) {
            this.plugin.messages().send(sender, "limity.command-usage");
            return 0;
        } else {
            world = worldOf(sender, null);
            command = trimmed;
        }
        if (world == null) {
            return 0;
        }
        module().limitY().setCommand(world, command);
        this.plugin.messages().send(sender, "limity.command-set", Map.of(
                "world", world.getName(),
                "command", command
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

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWorlds(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
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
