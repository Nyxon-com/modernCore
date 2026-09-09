package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.Plugins;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Sostituisce i comandi vanilla (msg/tell/w, kick, ban, give, pardon).
 */
public final class VanillaCommandOverrides {

    private VanillaCommandOverrides() {
    }

    public static void register(Commands registrar) {
        registrar.register(privateMessage("msg"), "Messaggio privato Valary", List.of());
        registrar.register(privateMessage("tell"), "Messaggio privato Valary", List.of());
        registrar.register(privateMessage("w"), "Messaggio privato Valary", List.of());
        registrar.register(privateMessage("whisper"), "Messaggio privato Valary", List.of());
        registrar.register(ModerationCommands.kickNode("kick"), "Kick Valary", List.of());
        registrar.register(ModerationCommands.banNode("ban"), "Ban Valary", List.of());
        registrar.register(ModerationCommands.banIpNode("ban-ip"), "IP ban Valary", List.of());
        registrar.register(ModerationCommands.banListNode("banlist"), "Lista ban Valary", List.of());
        registrar.register(ModerationCommands.unbanNode("pardon"), "Unban Valary", List.of());
        registrar.register(ModerationCommands.unbanNode("pardon-ip"), "Unban IP Valary", List.of());
        registrar.register(GiveCommands.giveNode("give"), "Give Valary", List.of());
        registrar.register(GiveCommands.selfNode("i"), "Give a te stesso", List.of());
    }

    public static LiteralCommandNode<CommandSourceStack> privateMessage(String literal) {
        return Commands.literal(literal)
                .requires(stack -> {
                    ValaryCorePlugin plugin = Plugins.core();
                    return plugin != null && Perms.has(plugin, stack.getSender(), "msg");
                })
                .executes(ctx -> {
                    ValaryCorePlugin plugin = Plugins.core();
                    if (plugin == null) {
                        return 0;
                    }
                    plugin.messages().send(ctx.getSource().getSender(), "msg.usage", Map.of("label", literal));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            OnlinePlayers.suggest(builder);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ValaryCorePlugin plugin = Plugins.core();
                            if (plugin == null) {
                                return 0;
                            }
                            plugin.messages().send(ctx.getSource().getSender(), "msg.usage", Map.of("label", literal));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> sendPrivate(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "message")
                                ))))
                .build();
    }

    private static int sendPrivate(CommandSender sender, String playerName, String message) {
        ValaryCorePlugin plugin = Plugins.core();
        if (plugin == null || plugin.modules() == null || !plugin.modules().isEnabled("players")) {
            return 0;
        }
        Player target = OnlinePlayers.exact(playerName);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Map.of("target", playerName));
            return 0;
        }
        PlayersModule module = plugin.modules().get("players");
        module.messages().send(sender, target, message);
        return Command.SINGLE_SUCCESS;
    }
}
