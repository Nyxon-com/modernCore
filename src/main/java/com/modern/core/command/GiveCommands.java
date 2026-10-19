package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.Plugins;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Items;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * /give e /i: item in inventario, niente drop a terra, testi da messages.yml.
 */
public final class GiveCommands {

    private GiveCommands() {
    }

    public static LiteralCommandNode<CommandSourceStack> giveNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> hasGive(stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "give.usage", literal))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .then(Commands.argument("item", ArgumentTypes.itemStack())
                                .executes(ctx -> giveToTargets(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveToTargets(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> selfNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> hasGive(stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "give.self-usage", literal))
                .then(Commands.argument("item", ArgumentTypes.itemStack())
                        .executes(ctx -> giveSelf(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> giveSelf(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .build();
    }

    private static int giveToTargets(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ModernCorePlugin plugin = Plugins.core();
        if (plugin == null) {
            return 0;
        }
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
        List<Player> targets = resolver.resolve(ctx.getSource());
        ItemStack item = ctx.getArgument("item", ItemStack.class);
        return give(plugin, ctx.getSource().getSender(), targets, item, count);
    }

    private static int giveSelf(CommandContext<CommandSourceStack> ctx, int count) {
        ModernCorePlugin plugin = Plugins.core();
        if (plugin == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        ItemStack item = ctx.getArgument("item", ItemStack.class);
        return give(plugin, sender, List.of(player), item, count);
    }

    private static int give(ModernCorePlugin plugin, CommandSender sender, List<Player> targets, ItemStack template, int requested) {
        if (targets == null || targets.isEmpty()) {
            plugin.messages().send(sender, "general.player-not-found", Map.of("target", "?"));
            return 0;
        }
        int max = Math.max(1, plugin.configs().players().getInt("give.max-amount", 2304));
        if (requested > max) {
            plugin.messages().send(sender, "give.too-many", Map.of("max", String.valueOf(max)));
            return 0;
        }
        boolean dropOverflow = plugin.configs().players().getBoolean("give.drop-overflow", false);
        boolean notifyTarget = plugin.configs().players().getBoolean("give.notify-target", true);
        ItemStack stack = template.clone();
        stack.setAmount(requested);
        String itemName = Items.miniName(stack);
        int delivered = 0;
        for (Player target : targets) {
            int leftover = Items.leftoverAfterAdd(target, stack, dropOverflow, plugin);
            int added = requested - leftover;
            if (leftover > 0) {
                plugin.messages().send(sender, "give.inventory-full", Map.of(
                        "player", plugin.messages().escape(target.getName()),
                        "left", String.valueOf(leftover),
                        "item", itemName
                ));
            }
            if (added <= 0) {
                continue;
            }
            delivered++;
            if (notifyTarget && !target.equals(sender)) {
                plugin.messages().send(target, "give.received", Map.of(
                        "item", itemName,
                        "amount", String.valueOf(added)
                ));
            }
        }
        if (delivered == 0) {
            return 0;
        }
        if (targets.size() == 1) {
            Player target = targets.getFirst();
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            plugin.messages().send(sender, self ? "give.received" : "give.given", Map.of(
                    "player", plugin.messages().escape(target.getName()),
                    "item", itemName,
                    "amount", String.valueOf(requested)
            ));
        } else {
            plugin.messages().send(sender, "give.given-many", Map.of(
                    "item", itemName,
                    "amount", String.valueOf(requested),
                    "count", String.valueOf(delivered)
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender, String key, String label) {
        ModernCorePlugin plugin = Plugins.core();
        if (plugin != null) {
            plugin.messages().send(sender, key, Map.of("label", label));
        }
        return 0;
    }

    private static boolean hasGive(CommandSender sender) {
        ModernCorePlugin plugin = Plugins.core();
        if (plugin == null) {
            return sender.hasPermission("minecraft.command.give");
        }
        return Perms.has(plugin, sender, "give") || sender.hasPermission("minecraft.command.give");
    }
}
