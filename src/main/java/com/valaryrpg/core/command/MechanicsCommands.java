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

    MechanicsCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(limitYCommand(), "Limite Y per mondo", aliases("commands.limity-aliases"));
        registrar.register(checkDeathCommand(), "Ultima posizione di morte", aliases("commands.checkdeath-aliases"));
        registrar.register(copyCoordCommand(), "Copia le coordinate", aliases("commands.copycoord-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> limitYCommand() {
        String name = string("commands.limity", "limity");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "limity"))
                .executes(ctx -> showLimit(ctx.getSource().getSender(), null))
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

    private int showLimit(CommandSender sender, String worldName) {
        World world = worldOf(sender, worldName);
        if (world == null) {
            return 0;
        }
        String range = module().limitY().describe(world);
        if (range.isBlank()) {
            this.plugin.messages().send(sender, "limity.none", Map.of("world", world.getName()));
            return Command.SINGLE_SUCCESS;
        }
        this.plugin.messages().send(sender, "limity.current", Map.of(
                "world", world.getName(),
                "range", range
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
        String value = this.plugin.configs().root().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
