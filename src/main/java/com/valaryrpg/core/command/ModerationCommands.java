package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.Plugins;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.module.moderation.ModerationService;
import com.valaryrpg.core.module.moderation.VanishService;
import com.valaryrpg.core.util.Durations;
import com.valaryrpg.core.util.OnlinePlayers;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModerationCommands {

    private final ValaryCorePlugin plugin;

    ModerationCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(unbanNode("unban"), "Unban Valary", aliases("commands.unban-aliases"));
        registrar.register(banIpNode("ipban"), "IP ban Valary", aliases("commands.ipban-aliases"));
        registrar.register(muteNode("mute"), "Mute Valary", aliases("commands.mute-aliases"));
        registrar.register(unmuteNode("unmute"), "Unmute Valary", aliases("commands.unmute-aliases"));
        registrar.register(warnNode("warn"), "Warn Valary", aliases("commands.warn-aliases"));
        registrar.register(warningsNode("warnings"), "Warnings Valary", aliases("commands.warnings-aliases"));
        registrar.register(dupeIpNode("dupeip"), "DupeIP Valary", aliases("commands.dupeip-aliases"));
        registrar.register(vanishNode("vanish"), "Vanish Valary", aliases("commands.vanish-aliases"));
        registrar.register(vanishEditNode("vanishedit"), "VanishEdit Valary", aliases("commands.vanishedit-aliases"));
        registrar.register(invseeNode("invsee"), "Invsee Valary", aliases("commands.invsee-aliases"));
    }

    public static LiteralCommandNode<CommandSourceStack> kickNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("kick", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.kick-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> kick(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"), null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> kick(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "reason")
                                ))))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> banNode(String literal) {
        return timedPlayerNode(literal, "ban", "moderation.ban-usage", ModerationCommands::ban);
    }

    public static LiteralCommandNode<CommandSourceStack> banIpNode(String literal) {
        return timedPlayerNode(literal, "ipban", "moderation.ipban-usage", ModerationCommands::ipBan);
    }

    public static LiteralCommandNode<CommandSourceStack> banListNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("banlist", stack.getSender()))
                .executes(ctx -> {
                    ModerationService service = moderation();
                    if (service == null) {
                        return 0;
                    }
                    service.banList(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> unbanNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("unban", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.unban-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ModerationService service = moderation();
                            if (service == null) {
                                return 0;
                            }
                            service.unban(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> muteNode(String literal) {
        return timedPlayerNode(literal, "mute", "moderation.mute-usage", ModerationCommands::mute);
    }

    private LiteralCommandNode<CommandSourceStack> unmuteNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("unmute", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.unmute-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> {
                            ModerationService service = moderation();
                            if (service == null) {
                                return 0;
                            }
                            service.unmute(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> warnNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("warn", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.warn-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> warn(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"), null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> warn(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "reason")
                                ))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> warningsNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("warnings", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.warnings-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> {
                            ModerationService service = moderation();
                            if (service == null) {
                                return 0;
                            }
                            service.warnings(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> dupeIpNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("dupeip", stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), "moderation.dupeip-usage", literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> {
                            ModerationService service = moderation();
                            if (service == null) {
                                return 0;
                            }
                            service.dupeIp(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> vanishNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("vanish", stack.getSender()))
                .executes(ctx -> vanishSelf(ctx.getSource().getSender(), null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(stack -> has("vanish-others", stack.getSender()))
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> vanishOther(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> vanishEditNode(String literal) {
        return Commands.literal(literal)
                .requires(stack -> has("vanishedit", stack.getSender()))
                .executes(ctx -> {
                    ValaryCorePlugin plugin = Plugins.core();
                    if (plugin != null) {
                        plugin.messages().send(ctx.getSource().getSender(), "vanish.edit-usage", Map.of("label", literal));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String flag : List.of("nightvision", "pickup")) {
                                if (flag.startsWith(builder.getRemainingLowerCase())) {
                                    builder.suggest(flag);
                                }
                            }
                            return builder.buildFuture();
                        })
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

    private static LiteralCommandNode<CommandSourceStack> timedPlayerNode(
            String literal,
            String permissionKey,
            String usageKey,
            TimedAction action
    ) {
        return Commands.literal(literal)
                .requires(stack -> has(permissionKey, stack.getSender()))
                .executes(ctx -> usage(ctx.getSource().getSender(), usageKey, literal))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnline(builder))
                        .executes(ctx -> action.run(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"), null, null))
                        .then(Commands.argument("durationOrReason", StringArgumentType.greedyString())
                                .executes(ctx -> timed(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "durationOrReason"),
                                        action
                                ))))
                .build();
    }

    private static int timed(CommandSender sender, String player, String rest, TimedAction action) {
        String[] parts = rest.trim().split("\\s+", 2);
        if (Durations.looksLikeDuration(parts[0])) {
            return action.run(sender, player, parts[0], parts.length > 1 ? parts[1] : null);
        }
        return action.run(sender, player, null, rest);
    }

    private static int kick(CommandSender sender, String playerName, String reason) {
        ModerationService service = moderation();
        Player target = OnlinePlayers.exact(playerName);
        if (service == null) {
            return 0;
        }
        if (target == null) {
            Plugins.core().messages().send(sender, "general.player-not-found", Map.of("target", playerName));
            return 0;
        }
        service.kick(sender, target, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static int ban(CommandSender sender, String player, String duration, String reason) {
        ModerationService service = moderation();
        if (service == null) {
            return 0;
        }
        service.ban(sender, player, duration, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static int ipBan(CommandSender sender, String player, String duration, String reason) {
        ModerationService service = moderation();
        if (service == null) {
            return 0;
        }
        service.ipBan(sender, player, duration, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static int mute(CommandSender sender, String player, String duration, String reason) {
        ModerationService service = moderation();
        if (service == null) {
            return 0;
        }
        service.mute(sender, player, duration, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static int warn(CommandSender sender, String player, String reason) {
        ModerationService service = moderation();
        if (service == null) {
            return 0;
        }
        service.warn(sender, player, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static int vanishSelf(CommandSender sender, Boolean ignored) {
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

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnline(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        OnlinePlayers.suggest(builder);
        return builder.buildFuture();
    }

    private static ModerationService moderation() {
        ModerationModule module = module();
        return module == null ? null : module.moderation();
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

    @FunctionalInterface
    private interface TimedAction {
        int run(CommandSender sender, String player, String duration, String reason);
    }
}
