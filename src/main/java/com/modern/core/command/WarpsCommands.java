package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.warps.WarpService;
import com.modern.core.module.warps.WarpsModule;
import com.modern.core.util.CommandText;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WarpsCommands {

    private final ModernCorePlugin plugin;

    WarpsCommands(ModernCorePlugin plugin) {
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
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String remaining = CommandText.firstToken(builder.getRemainingLowerCase());
                            for (String warp : warps().names(ctx.getSource().getSender())) {
                                if (warp.startsWith(remaining)) {
                                    builder.suggest(warp);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> warpArgs(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "args"), name)))
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
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                this.plugin.messages().send(sender, "general.player-only");
                                return 0;
                            }
                            String warpName = CommandText.nameOrNull(sender, this.plugin, StringArgumentType.getString(ctx, "name"));
                            if (warpName == null) {
                                return 0;
                            }
                            warps().setWarp(player, warpName);
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
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String remaining = CommandText.firstToken(builder.getRemainingLowerCase());
                            for (String warp : warps().names()) {
                                if (warp.startsWith(remaining)) {
                                    builder.suggest(warp);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String warpName = CommandText.nameOrNull(
                                    ctx.getSource().getSender(),
                                    this.plugin,
                                    StringArgumentType.getString(ctx, "name")
                            );
                            if (warpName == null) {
                                return 0;
                            }
                            warps().delete(ctx.getSource().getSender(), warpName);
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

    private int warpArgs(CommandSender sender, String raw, String label) {
        if (CommandText.hasStrayQuotes(raw)) {
            this.plugin.messages().send(sender, "general.quotes");
            return 0;
        }
        java.util.List<String> tokens = CommandText.tokens(raw);
        if (tokens.isEmpty()) {
            this.plugin.messages().send(sender, "warps.usage", Map.of("label", label));
            return 0;
        }
        String warpName = tokens.getFirst().toLowerCase(Locale.ROOT);
        if (tokens.size() == 1) {
            return teleportSelf(sender, warpName);
        }
        if (!Perms.has(this.plugin, sender, "warp-others")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return 0;
        }
        return teleportOther(sender, warpName, tokens.get(1));
    }

    private int teleportSelf(CommandSender sender, String warpName) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "warps.usage-console", Map.of("label", string("commands.warp", "warp")));
            return 0;
        }
        warps().teleport(player, player, warpName);
        return Command.SINGLE_SUCCESS;
    }

    private int teleportOther(CommandSender sender, String warpName, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", playerName));
            return 0;
        }
        warps().teleport(sender, target, warpName);
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
