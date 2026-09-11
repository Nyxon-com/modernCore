package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.mechanics.MechanicsModule;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Checkdeath, copycoord e long-jump; LimitY, portali e holo-block stanno in classi dedicate.
 */
final class MechanicsCommands {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;

    MechanicsCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    void register(Commands registrar) {
        new LimitYCommands(this.plugin).register(registrar);
        registrar.register(checkDeathCommand(), "Ultima posizione di morte", aliases("commands.checkdeath-aliases"));
        registrar.register(copyCoordCommand(), "Copia le coordinate", aliases("commands.copycoord-aliases"));
        new PortalCommands(this.plugin).register(registrar);
        new HoloBlockCommands(this.plugin).register(registrar);
        registrar.register(pressureJumpCommand(), "Long jump su pressure plate", aliases("commands.pressureplayerjump-aliases"));
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
                                .executes(ctx -> asPlayer(ctx.getSource().getSender(), player ->
                                        module().longJumps().bindLooking(player, StringArgumentType.getString(ctx, "preset"))))))
                .then(Commands.literal("remove")
                        .executes(ctx -> asPlayer(ctx.getSource().getSender(), player -> module().longJumps().unbindLooking(player))))
                .build();
    }

    private int asPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        action.accept(player);
        return Command.SINGLE_SUCCESS;
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
