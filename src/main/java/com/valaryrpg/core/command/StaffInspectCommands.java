package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.Plugins;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.module.moderation.VanishService;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vanish, vanishedit e invsee: ispezione staff senza punizioni.
 */
final class StaffInspectCommands {

    private final ValaryCorePlugin plugin;

    StaffInspectCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(vanishNode("vanish"), "Vanish Valary", aliases("commands.vanish-aliases"));
        registrar.register(vanishEditNode("vanishedit"), "VanishEdit Valary", aliases("commands.vanishedit-aliases"));
        registrar.register(invseeNode("invsee"), "Invsee Valary", aliases("commands.invsee-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> vanishNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("vanish", stack.getSender()))
                .executes(ctx -> vanishSelf(ctx.getSource().getSender()))
                .then(Commands.literal("edit")
                        .requires(stack -> has("vanishedit", stack.getSender()))
                        .executes(ctx -> vanishEditMenu(ctx.getSource().getSender()))
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestVanishFlags(builder))
                                .executes(ctx -> vanishEdit(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "flag")))))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(stack -> has("vanish-others", stack.getSender()))
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> vanishOther(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> vanishEditNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("vanishedit", stack.getSender()))
                .executes(ctx -> vanishEditMenu(ctx.getSource().getSender()))
                .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestVanishFlags(builder))
                        .executes(ctx -> vanishEdit(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "flag"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> invseeNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("invsee", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "invsee.usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> invsee(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"))))
                .build();
    }

    private static int vanishSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            usage(sender, "vanish.usage", "vanish");
            return 0;
        }
        VanishService vanish = vanish();
        if (vanish == null) {
            return 0;
        }
        vanish.toggle(player, player);
        return Command.SINGLE_SUCCESS;
    }

    private static int vanishOther(CommandSender sender, String playerName) {
        VanishService vanish = vanish();
        Player target = OnlinePlayers.exact(playerName);
        if (vanish == null) {
            return 0;
        }
        if (target == null) {
            Plugins.core().messages().send(sender, "general.player-not-found", Map.of("target", playerName));
            return 0;
        }
        vanish.toggle(target, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int vanishEditMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ValaryCorePlugin plugin = Plugins.core();
            if (plugin != null) {
                plugin.messages().send(sender, "general.player-only");
            }
            return 0;
        }
        VanishService vanish = vanish();
        if (vanish == null) {
            return 0;
        }
        vanish.openEdit(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int vanishEdit(CommandSender sender, String flag) {
        if (!(sender instanceof Player player)) {
            Plugins.core().messages().send(sender, "general.player-only");
            return 0;
        }
        VanishService vanish = vanish();
        if (vanish == null) {
            return 0;
        }
        return switch (flag.toLowerCase(Locale.ROOT)) {
            case "nightvision", "nv" -> {
                vanish.toggleNightVision(player);
                yield Command.SINGLE_SUCCESS;
            }
            case "pickup", "nopickup" -> {
                vanish.toggleNoPickup(player);
                yield Command.SINGLE_SUCCESS;
            }
            default -> {
                Plugins.core().messages().send(sender, "vanish.edit-unknown");
                yield 0;
            }
        };
    }

    private static int invsee(CommandSender sender, String playerName) {
        if (!(sender instanceof Player viewer)) {
            Plugins.core().messages().send(sender, "general.player-only");
            return 0;
        }
        Player target = OnlinePlayers.exact(playerName);
        if (target == null) {
            Plugins.core().messages().send(sender, "general.player-not-found", Map.of("target", playerName));
            return 0;
        }
        ModerationModule module = module();
        if (module == null) {
            return 0;
        }
        module.invsee().open(viewer, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender, String key, String label) {
        ValaryCorePlugin plugin = Plugins.core();
        if (plugin != null) {
            plugin.messages().send(sender, key, Map.of("label", label));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean has(String permissionKey, CommandSender sender) {
        ValaryCorePlugin plugin = Plugins.core();
        return plugin != null && Perms.has(plugin, sender, permissionKey);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestVanishFlags(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        for (String flag : List.of("nightvision", "pickup")) {
            if (flag.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(flag);
            }
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnline(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        OnlinePlayers.suggest(builder);
        return builder.buildFuture();
    }

    private static VanishService vanish() {
        ModerationModule module = module();
        return module == null ? null : module.vanish();
    }

    private static ModerationModule module() {
        ValaryCorePlugin plugin = Plugins.core();
        if (plugin == null || plugin.modules() == null || !plugin.modules().isEnabled("moderation")) {
            return null;
        }
        return plugin.modules().get("moderation");
    }

    private List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
