package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.worlds.WeatherService;
import com.modern.core.module.worlds.WorldsModule;
import com.modern.core.util.OnlinePlayers;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WorldsCommands {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  WorldsCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    registrar.register(spawnCommand(), "Teletrasporto allo spawn globale", aliases("commands.spawn-aliases"));
    registrar.register(setSpawnCommand(), "Imposta lo spawn globale", aliases("commands.setspawn-aliases"));
    registrar.register(weatherLockCommand(), "Blocca o sblocca il meteo di un mondo", aliases("commands.weatherlock-aliases"));
    registrar.register(timeLockCommand(), "Blocca o sblocca l'ora di un mondo", aliases("commands.timelock-aliases"));
    registrar.register(fallDamageCommand(), "Attiva o disattiva il danno da caduta per mondo", aliases("commands.falldamage-aliases"));
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
        .then(Commands.argument("target", StringArgumentType.word())
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), "spawn-others"))
            .suggests((ctx, builder) -> {
              OnlinePlayers.suggest(builder);
              return builder.buildFuture();
            })
            .executes(ctx -> {
              String targetName = StringArgumentType.getString(ctx, "target");
              Player target = OnlinePlayers.exact(targetName);
              if (target == null) {
                this.plugin.messages().send(ctx.getSource().getSender(), "general.player-not-found", Map.of("target", targetName));
                return 0;
              }
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
    long ticks = com.modern.core.module.worlds.TimeService.ticksOfPreset(type);
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

  private LiteralCommandNode<CommandSourceStack> fallDamageCommand() {
    String name = string("commands.falldamage", "falldamage");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "falldamage"))
        .executes(ctx -> showFallDamage(ctx.getSource().getSender(), null, name))
        .then(Commands.argument("state", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              for (String suggestion : List.of("true", "false", "on", "off")) {
                if (suggestion.startsWith(builder.getRemainingLowerCase())) {
                  builder.suggest(suggestion);
                }
              }
              return builder.buildFuture();
            })
            .executes(ctx -> applyFallDamage(
                ctx.getSource().getSender(),
                StringArgumentType.getString(ctx, "state"),
                null,
                name
            ))
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                      builder.suggest(world.getName());
                    }
                  }
                  return builder.buildFuture();
                })
                .executes(ctx -> applyFallDamage(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "state"),
                    StringArgumentType.getString(ctx, "world"),
                    name
                ))))
        .build();
  }

  private int showFallDamage(CommandSender sender, String worldName, String label) {
    String name = worldOf(sender, worldName, label);
    if (name == null) {
      return 0;
    }
    boolean enabled = worlds().catalog().fallDamage(name);
    this.plugin.messages().send(sender, enabled ? "falldamage.current-on" : "falldamage.current-off", Map.of("world", name));
    return Command.SINGLE_SUCCESS;
  }

  private int applyFallDamage(CommandSender sender, String stateRaw, String worldName, String label) {
    Boolean enabled = parseToggle(stateRaw);
    if (enabled == null) {
      this.plugin.messages().send(sender, "falldamage.usage", Map.of("label", label));
      return 0;
    }
    String name = worldOf(sender, worldName, label);
    if (name == null) {
      return 0;
    }
    worlds().catalog().setFallDamage(name, enabled);
    this.plugin.messages().send(sender, enabled ? "falldamage.enabled" : "falldamage.disabled", Map.of("world", name));
    return Command.SINGLE_SUCCESS;
  }

  private String worldOf(CommandSender sender, String worldName, String label) {
    if (worldName == null || worldName.isBlank()) {
      if (!(sender instanceof Player player) || player.getWorld() == null) {
        this.plugin.messages().send(sender, "falldamage.usage", Map.of("label", label));
        return null;
      }
      return player.getWorld().getName();
    }
    return worldName;
  }

  private static Boolean parseToggle(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "true", "on", "enable", "enabled", "1", "si", "yes" -> true;
      case "false", "off", "disable", "disabled", "0", "no" -> false;
      default -> null;
    };
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
