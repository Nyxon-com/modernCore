package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.homes.HomeService;
import com.valaryrpg.core.module.homes.HomesModule;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class HomesCommands {

    private final ValaryCorePlugin plugin;

    HomesCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(homeCommand(), "Teletrasporto a una home", aliases("commands.home-aliases"));
        registrar.register(setHomeCommand(), "Imposta una home", aliases("commands.sethome-aliases"));
        registrar.register(delHomeCommand(), "Elimina una home", aliases("commands.delhome-aliases"));
        registrar.register(homesListCommand(), "Elenca le home", aliases("commands.homes-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> homeCommand() {
        String name = string("commands.home", "home");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "home"))
                .executes(ctx -> teleport(ctx.getSource().getSender(), null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestHomes(ctx.getSource().getSender(), builder.getRemainingLowerCase())
                                .thenCompose(names -> {
                                    names.forEach(builder::suggest);
                                    return builder.buildFuture();
                                }))
                        .executes(ctx -> teleport(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> setHomeCommand() {
        String name = string("commands.sethome", "sethome");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "sethome"))
                .executes(ctx -> set(ctx.getSource().getSender(), null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> set(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> delHomeCommand() {
        String name = string("commands.delhome", "delhome");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "delhome"))
                .executes(ctx -> delete(ctx.getSource().getSender(), null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestHomes(ctx.getSource().getSender(), builder.getRemainingLowerCase())
                                .thenCompose(names -> {
                                    names.forEach(builder::suggest);
                                    return builder.buildFuture();
                                }))
                        .executes(ctx -> delete(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> homesListCommand() {
        String name = string("commands.homes", "homes");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "home"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        this.plugin.messages().send(sender, "general.player-only");
                        return 0;
                    }
                    homes().list(player, player.getUniqueId());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private int teleport(CommandSender sender, String homeName) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        homes().teleport(player, homeName);
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandSender sender, String homeName) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        homes().setHome(player, homeName);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandSender sender, String homeName) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        homes().delete(player, homeName);
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<List<String>> suggestHomes(CommandSender sender, String remaining) {
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> homes().names(player.getUniqueId()).stream()
                .filter(name -> name.startsWith(remaining))
                .toList());
    }

    private HomeService homes() {
        HomesModule module = this.plugin.modules().get("homes");
        return module.homes();
    }

    private String string(String path, String fallback) {
        String value = this.plugin.configs().root().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
