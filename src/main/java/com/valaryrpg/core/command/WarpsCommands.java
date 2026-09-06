package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.warps.WarpService;
import com.valaryrpg.core.module.warps.WarpsModule;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

final class WarpsCommands {

    private final ValaryCorePlugin plugin;

    WarpsCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(warpCommand(), "Teletrasporto a un warp", aliases("commands.warp-aliases"));
        registrar.register(setWarpCommand(), "Crea o aggiorna un warp", aliases("commands.setwarp-aliases"));
        registrar.register(delWarpCommand(), "Elimina un warp", aliases("commands.delwarp-aliases"));
        registrar.register(warpsListCommand(), "Elenca i warp", aliases("commands.warps-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> warpCommand() {
        String name = string("commands.warp", "warp");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "warp"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "warps.usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            for (String warp : warps().names()) {
                                if (warp.startsWith(remaining)) {
                                    builder.suggest(warp);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> teleportSelf(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name")))
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "warp-others"))
                                .executes(ctx -> {
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                    List<Player> players = resolver.resolve(ctx.getSource());
                                    if (players.isEmpty()) {
                                        this.plugin.messages().send(ctx.getSource().getSender(), "general.player-not-found", Map.of("target", "?"));
                                        return 0;
                                    }
                                    warps().teleport(ctx.getSource().getSender(), players.getFirst(), StringArgumentType.getString(ctx, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> setWarpCommand() {
        String name = string("commands.setwarp", "setwarp");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "setwarp"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "warps.set-usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                this.plugin.messages().send(sender, "general.player-only");
                                return 0;
                            }
                            warps().setWarp(player, StringArgumentType.getString(ctx, "name"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> delWarpCommand() {
        String name = string("commands.delwarp", "delwarp");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "delwarp"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "warps.del-usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            for (String warp : warps().names()) {
                                if (warp.startsWith(remaining)) {
                                    builder.suggest(warp);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            warps().delete(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> warpsListCommand() {
        String name = string("commands.warps", "warps");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "warp"))
                .executes(ctx -> {
                    warps().list(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private int teleportSelf(CommandSender sender, String warpName) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        warps().teleport(player, player, warpName);
        return Command.SINGLE_SUCCESS;
    }

    private WarpService warps() {
        WarpsModule module = this.plugin.modules().get("warps");
        return module.warps();
    }

    private String string(String path, String fallback) {
        String value = this.plugin.configs().root().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
