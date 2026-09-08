package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.worlds.WeatherService;
import com.valaryrpg.core.module.worlds.WorldsModule;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Sounds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registrazione comandi Paper/Brigadier. Nomi, alias e permessi arrivano da config.yml.
 */
public final class CommandService {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;

    public CommandService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    public void register() {
        this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(rootCommand(), "Comandi amministrativi ValaryRPG-Core", aliases("commands.root-aliases"));
            if (enabled("worlds")) {
                registrar.register(spawnCommand(), "Teletrasporto allo spawn globale", aliases("commands.spawn-aliases"));
                registrar.register(setSpawnCommand(), "Imposta lo spawn globale", aliases("commands.setspawn-aliases"));
                registrar.register(weatherLockCommand(), "Blocca o sblocca il meteo di un mondo", aliases("commands.weatherlock-aliases"));
                registrar.register(timeLockCommand(), "Blocca o sblocca l'ora di un mondo", aliases("commands.timelock-aliases"));
            }
            if (enabled("homes")) {
                new HomesCommands(this.plugin).register(registrar);
            }
            if (enabled("warps")) {
                new WarpsCommands(this.plugin).register(registrar);
            }
            if (enabled("players")) {
                new PlayersCommands(this.plugin).register(registrar);
            }
            if (enabled("moderation")) {
                new ModerationCommands(this.plugin).register(registrar);
            }
            if (enabled("mechanics")) {
                new MechanicsCommands(this.plugin).register(registrar);
            }
            if (enabled("kits")) {
                new KitsCommands(this.plugin).register(registrar);
            }
            if (enabled("sell")) {
                new SellCommands(this.plugin).register(registrar);
            }
        });
    }

    private LiteralCommandNode<CommandSourceStack> rootCommand() {
        String name = string("commands.root", "valary");
        return Commands.literal(name)
                .then(Commands.literal("reload")
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            boolean ok = this.plugin.reloadAll();
                            if (ok) {
                                this.plugin.messages().send(sender, "reload.success");
                                if (sender instanceof Player player) {
                                    Sounds.play(this.plugin, player, "reload-success");
                                }
                            } else {
                                this.plugin.messages().send(sender, "reload.failed", Map.of("error", "vedi console"));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "general.unknown-subcommand", Map.of(
                            "label", name
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> spawnCommand() {
        String name = string("commands.spawn", "spawn");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "spawn"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        this.plugin.messages().send(sender, "general.player-only");
                        return 0;
                    }
                    worlds().spawns().teleport(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "spawn-others"))
                        .executes(ctx -> {
                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                            List<Player> players = resolver.resolve(ctx.getSource());
                            if (players.isEmpty()) {
                                this.plugin.messages().send(ctx.getSource().getSender(), "general.player-not-found", Map.of("target", "?"));
                                return 0;
                            }
                            Player target = players.getFirst();
                            worlds().spawns().teleport(ctx.getSource().getSender(), target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> setSpawnCommand() {
        String name = string("commands.setspawn", "setspawn");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "setspawn"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        this.plugin.messages().send(sender, "general.player-only");
                        return 0;
                    }
                    worlds().spawns().setSpawn(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> timeLockCommand() {
        String name = string("commands.timelock", "timelock");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "timelock"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "time.usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String suggestion : List.of("day", "noon", "night", "midnight", "unlock")) {
                                if (suggestion.startsWith(builder.getRemainingLowerCase())) {
                                    builder.suggest(suggestion);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> applyTime(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"), null, name))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (World world : Bukkit.getWorlds()) {
                                        if (world.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                            builder.suggest(world.getName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> applyTime(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "type"),
                                        StringArgumentType.getString(ctx, "world"),
                                        name
                                ))))
                .build();
    }

    private int applyTime(CommandSender sender, String typeRaw, String worldName, String label) {
        World world;
        if (worldName == null || worldName.isBlank()) {
            if (!(sender instanceof Player player) || player.getWorld() == null) {
                this.plugin.messages().send(sender, "time.usage", Map.of("label", label));
                return 0;
            }
            world = player.getWorld();
        } else {
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                this.plugin.messages().send(sender, "general.invalid-world", Map.of("world", worldName));
                return 0;
            }
        }
        String type = typeRaw.toLowerCase(Locale.ROOT);
        if (type.equals("unlock")) {
            worlds().time().unlock(world);
            this.plugin.messages().send(sender, "time.unlocked", Map.of("world", world.getName()));
            return Command.SINGLE_SUCCESS;
        }
        long ticks = com.valaryrpg.core.module.worlds.TimeService.ticksOfPreset(type);
        if (ticks < 0) {
            this.plugin.messages().send(sender, "time.invalid-type");
            return 0;
        }
        worlds().time().lock(world, ticks);
        this.plugin.messages().send(sender, "time.locked", Map.of(
                "world", world.getName(),
                "time", type,
                "ticks", String.valueOf(ticks)
        ));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralCommandNode<CommandSourceStack> weatherLockCommand() {
        String name = string("commands.weatherlock", "weatherlock");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "weatherlock"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "weather.usage", Map.of("label", name));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String suggestion : List.of("clear", "rain", "thunder", "unlock")) {
                                if (suggestion.startsWith(builder.getRemainingLowerCase())) {
                                    builder.suggest(suggestion);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> applyWeather(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"), null, name))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (World world : Bukkit.getWorlds()) {
                                        if (world.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                            builder.suggest(world.getName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> applyWeather(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "type"),
                                        StringArgumentType.getString(ctx, "world"),
                                        name
                                ))))
                .build();
    }

    private int applyWeather(CommandSender sender, String typeRaw, String worldName, String label) {
        World world;
        if (worldName == null || worldName.isBlank()) {
            if (!(sender instanceof Player player) || player.getWorld() == null) {
                this.plugin.messages().send(sender, "weather.usage", Map.of("label", label));
                return 0;
            }
            world = player.getWorld();
        } else {
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                this.plugin.messages().send(sender, "general.invalid-world", Map.of("world", worldName));
                return 0;
            }
        }

        String type = typeRaw.toLowerCase(Locale.ROOT);
        if (type.equals("unlock")) {
            worlds().weather().unlock(world);
            this.plugin.messages().send(sender, "weather.unlocked", Map.of("world", world.getName()));
            return Command.SINGLE_SUCCESS;
        }

        WeatherService.WeatherLock lock = switch (type) {
            case "clear", "sun", "sole" -> WeatherService.WeatherLock.CLEAR;
            case "rain", "pioggia" -> WeatherService.WeatherLock.RAIN;
            case "thunder", "storm", "tuoni" -> WeatherService.WeatherLock.THUNDER;
            default -> null;
        };
        if (lock == null) {
            this.plugin.messages().send(sender, "weather.invalid-type");
            return 0;
        }

        worlds().weather().lock(world, lock);
        this.plugin.messages().send(sender, "weather.locked", Map.of(
                "world", world.getName(),
                "weather", lock.name().toLowerCase(Locale.ROOT)
        ));
        return Command.SINGLE_SUCCESS;
    }

    private boolean enabled(String moduleId) {
        return this.plugin.modules() != null && this.plugin.modules().isEnabled(moduleId);
    }

    private WorldsModule worlds() {
        return this.plugin.modules().get("worlds");
    }

    private String string(String path, String fallback) {
        return this.settings.name(path, fallback);
    }

    private List<String> aliases(String path) {
        return this.settings.aliases(path);
    }
}
