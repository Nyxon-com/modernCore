package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.players.PlayerProfile;
import com.modern.core.module.players.PlayersModule;
import com.modern.core.module.players.PlaytimeService;
import com.modern.core.util.Durations;
import com.modern.core.util.OnlinePlayers;
import com.modern.core.util.Perms;
import com.modern.core.util.Tasks;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * /playtime e /playerlist.
 */
final class PlaytimeCommands {

    private final ModernCorePlugin plugin;

    PlaytimeCommands(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands registrar) {
        registrar.register(playtimeNode(), "Playtime Valary", aliases("commands.playtime-aliases"));
        registrar.register(playerListNode(), "Playerlist Valary", aliases("commands.playerlist-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> playtimeNode() {
        String name = string("commands.playtime", "playtime");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "playtime"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        this.plugin.messages().send(sender, "playtime.usage", Map.of("label", name));
                        return 0;
                    }
                    showPlaytime(sender, player.getName(), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "playtime-others"))
                        .suggests((ctx, builder) -> {
                            OnlinePlayers.suggest(builder);
                            PlayersModule module = module();
                            if (module != null && module.repository() != null) {
                                for (String entry : module.repository().suggestNames(builder.getRemaining(), 30)) {
                                    if (entry.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                        builder.suggest(entry);
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            showPlaytime(
                                    ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "player"),
                                    false
                            );
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> playerListNode() {
        String name = string("commands.playerlist", "playerlist");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "playerlist"))
                .executes(ctx -> {
                    showPlayerList(ctx.getSource().getSender(), 1);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            showPlayerList(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "page"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void showPlaytime(CommandSender sender, String name, boolean self) {
        PlayersModule module = module();
        if (module == null || module.repository() == null || module.playtime() == null) {
            return;
        }
        PlaytimeService playtime = module.playtime();
        Tasks.async(this.plugin, () -> {
            PlayerProfile profile = null;
            Player online = self && sender instanceof Player player ? player : OnlinePlayers.exact(name);
            if (online != null) {
                profile = module.repository().find(online.getUniqueId()).orElse(null);
                if (profile == null) {
                    profile = new PlayerProfile(
                            online.getUniqueId(),
                            online.getName(),
                            null,
                            false,
                            false,
                            online.getGameMode().name(),
                            online.getFirstPlayed() > 0L ? online.getFirstPlayed() : System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            0L,
                            0L,
                            ""
                    );
                }
            } else {
                profile = module.repository().findByName(name).orElse(null);
            }
            PlayerProfile found = profile;
            Tasks.sync(this.plugin, () -> {
                if (found == null) {
                    this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", name));
                    return;
                }
                long total = playtime.liveMillis(found.uuid(), found.playtimeMs());
                String first = found.firstJoin() > 0L
                        ? Durations.formatWhen(this.plugin, found.firstJoin())
                        : this.plugin.messages().text("playtime.never");
                String lastQuit;
                if (Bukkit.getPlayer(found.uuid()) != null) {
                    lastQuit = this.plugin.messages().text("playtime.online-now");
                } else if (found.lastQuit() > 0L) {
                    lastQuit = Durations.formatWhen(this.plugin, found.lastQuit());
                } else if (found.lastJoin() > 0L) {
                    lastQuit = Durations.formatWhen(this.plugin, found.lastJoin());
                } else {
                    lastQuit = this.plugin.messages().text("playtime.never");
                }
                Map<String, String> placeholders = Map.of(
                        "player", found.name(),
                        "playtime", playtime.format(total),
                        "first", first,
                        "last", lastQuit
                );
                this.plugin.messages().send(sender, self ? "playtime.self" : "playtime.other", placeholders);
                this.plugin.messages().send(sender, "playtime.details", placeholders);
            });
        });
    }

    private void showPlayerList(CommandSender sender, int page) {
        PlayersModule module = module();
        if (module == null || module.repository() == null) {
            return;
        }
        int pageSize = Math.max(5, this.plugin.configs().players().getInt("playerlist.page-size", 15));
        Tasks.async(this.plugin, () -> {
            int total = module.repository().countPlayers();
            int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
            int safePage = Math.min(Math.max(1, page), pages);
            List<PlayerProfile> profiles = module.repository().listAll(pageSize, (safePage - 1) * pageSize);
            Tasks.sync(this.plugin, () -> {
                this.plugin.messages().send(sender, "playerlist.header", Map.of(
                        "page", String.valueOf(safePage),
                        "pages", String.valueOf(pages),
                        "total", String.valueOf(total)
                ));
                if (profiles.isEmpty()) {
                    this.plugin.messages().send(sender, "playerlist.empty");
                    return;
                }
                for (PlayerProfile profile : profiles) {
                    boolean online = Bukkit.getPlayer(profile.uuid()) != null;
                    this.plugin.messages().send(sender, online ? "playerlist.entry-online" : "playerlist.entry-offline", Map.of(
                            "player", profile.name(),
                            "first", profile.firstJoin() > 0L
                                    ? Durations.formatWhen(this.plugin, profile.firstJoin())
                                    : this.plugin.messages().text("playtime.never"),
                            "last", online
                                    ? this.plugin.messages().text("playtime.online-now")
                                    : (profile.lastQuit() > 0L
                                    ? Durations.formatWhen(this.plugin, profile.lastQuit())
                                    : this.plugin.messages().text("playtime.never"))
                    ));
                }
                this.plugin.messages().send(sender, "playerlist.footer", Map.of(
                        "page", String.valueOf(safePage),
                        "pages", String.valueOf(pages)
                ));
            });
        });
    }

    private PlayersModule module() {
        if (this.plugin.modules() == null || !this.plugin.modules().isEnabled("players")) {
            return null;
        }
        return this.plugin.modules().get("players");
    }

    private String string(String path, String fallback) {
        String value = this.plugin.configs().root().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> aliases(String path) {
        return this.plugin.configs().root().getStringList(path);
    }
}
