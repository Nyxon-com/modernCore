package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.players.BackService;
import com.modern.core.module.players.FlyService;
import com.modern.core.module.players.GodService;
import com.modern.core.module.players.NickService;
import com.modern.core.module.players.PlayersModule;
import com.modern.core.module.players.PrivateMessageService;
import com.modern.core.module.players.VitalService;
import com.modern.core.util.OnlinePlayers;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PlayersCommands {

  private static final Set<String> CLEAR_NICK = Set.of("off", "reset", "clear", "none", "disable");
  private static final Set<String> TRUE_STATES = Set.of("on", "true", "enable", "enabled");
  private static final Set<String> FALSE_STATES = Set.of("off", "false", "disable", "disabled");

  private final ModernCorePlugin plugin;

  PlayersCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  void register(Commands registrar) {
    registrar.register(VanillaCommandOverrides.privateMessage("m"), "Messaggio privato Valary", List.of());
    registrar.register(replyCommand(), "Rispondi all'ultimo messaggio privato", aliases("commands.reply-aliases"));
    registrar.register(socialSpyCommand(), "Spia i messaggi privati", aliases("commands.socialspy-aliases"));
    registrar.register(nickCommand(), "Imposta un nickname MiniMessage", aliases("commands.nick-aliases"));
    registrar.register(flyCommand(), "Attiva o disattiva il volo", aliases("commands.fly-aliases"));
    registrar.register(backCommand(), "Torna all'ultima posizione", aliases("commands.back-aliases"));
    registrar.register(godCommand(), "Attiva o disattiva il god mode", aliases("commands.god-aliases"));
    registrar.register(healCommand(), "Cura un giocatore", aliases("commands.heal-aliases"));
    registrar.register(feedCommand(), "Riempie la fame", aliases("commands.feed-aliases"));
    for (String alias : aliases("commands.give-aliases")) {
      if (alias.isBlank() || alias.equalsIgnoreCase("give") || alias.equalsIgnoreCase("i")) {
        continue;
      }
      registrar.register(GiveCommands.giveNode(alias), "Give Valary", List.of());
    }
    new PlaytimeCommands(this.plugin).register(registrar);
  }

  private LiteralCommandNode<CommandSourceStack> replyCommand() {
    String name = string("commands.reply", "r");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "msg"))
        .executes(ctx -> {
          this.plugin.messages().send(ctx.getSource().getSender(), "msg.reply-usage", Map.of("label", name));
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
              CommandSender sender = ctx.getSource().getSender();
              if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "general.player-only");
                return 0;
              }
              messages().reply(player, StringArgumentType.getString(ctx, "message"));
              return Command.SINGLE_SUCCESS;
            }))
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> socialSpyCommand() {
    String name = string("commands.socialspy", "socialspy");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "socialspy"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
          }
          messages().toggleSpy(player);
          return Command.SINGLE_SUCCESS;
        })
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> nickCommand() {
    String name = string("commands.nick", "nick");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "nick"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
          }
          nicks().show(player);
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(ctx -> handleNick(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "args"))))
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> flyCommand() {
    String name = string("commands.fly", "fly");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "fly"))
        .executes(ctx -> flySelf(ctx.getSource().getSender(), null))
        .then(Commands.argument("playerOrState", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              OnlinePlayers.suggest(builder);
              for (String state : List.of("on", "off")) {
                if (state.startsWith(builder.getRemainingLowerCase())) {
                  builder.suggest(state);
                }
              }
              return builder.buildFuture();
            })
            .executes(ctx -> flyFirstArg(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "playerOrState")))
            .then(Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String state : List.of("on", "off")) {
                    if (state.startsWith(builder.getRemainingLowerCase())) {
                      builder.suggest(state);
                    }
                  }
                  return builder.buildFuture();
                })
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "fly-others"))
                .executes(ctx -> flyOther(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "playerOrState"),
                    parseState(StringArgumentType.getString(ctx, "state"))
                ))))
        .build();
  }

  private int handleNick(CommandSender sender, String args) {
    String trimmed = args.trim();
    String[] parts = trimmed.split("\\s+", 2);
    Player other = OnlinePlayers.exact(parts[0]);
    if (other != null && parts.length == 2 && Perms.has(this.plugin, sender, "nick-others")) {
      if (CLEAR_NICK.contains(parts[1].toLowerCase(Locale.ROOT))) {
        nicks().clear(other, sender);
      } else {
        nicks().set(other, parts[1], sender);
      }
      return Command.SINGLE_SUCCESS;
    }
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    if (CLEAR_NICK.contains(trimmed.toLowerCase(Locale.ROOT))) {
      nicks().clear(player, player);
      return Command.SINGLE_SUCCESS;
    }
    nicks().set(player, trimmed, player);
    return Command.SINGLE_SUCCESS;
  }

  private int flySelf(CommandSender sender, Boolean state) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "fly.usage", Map.of("label", string("commands.fly", "fly")));
      return 0;
    }
    if (state == null) {
      fly().toggle(player, player);
    } else {
      fly().set(player, state, player);
    }
    return Command.SINGLE_SUCCESS;
  }

  private int flyFirstArg(CommandSender sender, String arg) {
    Boolean state = parseState(arg);
    if (state != null) {
      return flySelf(sender, state);
    }
    if (!Perms.has(this.plugin, sender, "fly-others")) {
      this.plugin.messages().send(sender, "general.no-permission");
      return 0;
    }
    return flyOther(sender, arg, null);
  }

  private int flyOther(CommandSender sender, String playerName, Boolean state) {
    Player target = OnlinePlayers.exact(playerName);
    if (target == null) {
      this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", playerName));
      return 0;
    }
    if (state == null) {
      fly().toggle(target, sender);
    } else {
      fly().set(target, state, sender);
    }
    return Command.SINGLE_SUCCESS;
  }

  private LiteralCommandNode<CommandSourceStack> backCommand() {
    String name = string("commands.back", "back");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "back"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
          }
          back().go(player);
          return Command.SINGLE_SUCCESS;
        })
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> godCommand() {
    String name = string("commands.god", "god");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "god"))
        .executes(ctx -> godSelf(ctx.getSource().getSender(), null))
        .then(Commands.argument("playerOrState", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              OnlinePlayers.suggest(builder);
              for (String state : List.of("on", "off")) {
                if (state.startsWith(builder.getRemainingLowerCase())) {
                  builder.suggest(state);
                }
              }
              return builder.buildFuture();
            })
            .executes(ctx -> godFirstArg(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "playerOrState")))
            .then(Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                  for (String state : List.of("on", "off")) {
                    if (state.startsWith(builder.getRemainingLowerCase())) {
                      builder.suggest(state);
                    }
                  }
                  return builder.buildFuture();
                })
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "god-others"))
                .executes(ctx -> godOther(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "playerOrState"),
                    parseState(StringArgumentType.getString(ctx, "state"))
                ))))
        .build();
  }

  private LiteralCommandNode<CommandSourceStack> healCommand() {
    return vitalCommand("commands.heal", "heal", "heal", "heal-others", (target, sender) -> vitals().heal(target, sender));
  }

  private LiteralCommandNode<CommandSourceStack> feedCommand() {
    return vitalCommand("commands.feed", "feed", "feed", "feed-others", (target, sender) -> vitals().feed(target, sender));
  }

  private LiteralCommandNode<CommandSourceStack> vitalCommand(
      String path,
      String fallback,
      String selfPerm,
      String othersPerm,
      java.util.function.BiConsumer<Player, CommandSender> action
  ) {
    String name = string(path, fallback);
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), selfPerm))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, fallback + ".usage", Map.of("label", name));
            return 0;
          }
          action.accept(player, player);
          return Command.SINGLE_SUCCESS;
        })
        .then(Commands.argument("player", StringArgumentType.word())
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), othersPerm))
            .suggests((ctx, builder) -> {
              OnlinePlayers.suggest(builder);
              return builder.buildFuture();
            })
            .executes(ctx -> {
              String playerName = StringArgumentType.getString(ctx, "player");
              Player target = OnlinePlayers.exact(playerName);
              if (target == null) {
                this.plugin.messages().send(ctx.getSource().getSender(), "general.player-not-found", Map.of("target", playerName));
                return 0;
              }
              action.accept(target, ctx.getSource().getSender());
              return Command.SINGLE_SUCCESS;
            }))
        .build();
  }

  private int godSelf(CommandSender sender, Boolean state) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "god.usage", Map.of("label", string("commands.god", "god")));
      return 0;
    }
    if (state == null) {
      god().toggle(player, player);
    } else {
      god().set(player, state, player);
    }
    return Command.SINGLE_SUCCESS;
  }

  private int godFirstArg(CommandSender sender, String arg) {
    Boolean state = parseState(arg);
    if (state != null) {
      return godSelf(sender, state);
    }
    if (!Perms.has(this.plugin, sender, "god-others")) {
      this.plugin.messages().send(sender, "general.no-permission");
      return 0;
    }
    return godOther(sender, arg, null);
  }

  private int godOther(CommandSender sender, String playerName, Boolean state) {
    Player target = OnlinePlayers.exact(playerName);
    if (target == null) {
      this.plugin.messages().send(sender, "general.player-not-found", Map.of("target", playerName));
      return 0;
    }
    if (state == null) {
      god().toggle(target, sender);
    } else {
      god().set(target, state, sender);
    }
    return Command.SINGLE_SUCCESS;
  }

  private static Boolean parseState(String raw) {
    String value = raw.toLowerCase(Locale.ROOT);
    if (TRUE_STATES.contains(value)) {
      return true;
    }
    if (FALSE_STATES.contains(value)) {
      return false;
    }
    return null;
  }

  private NickService nicks() {
    return module().nicks();
  }

  private FlyService fly() {
    return module().fly();
  }

  private GodService god() {
    return module().god();
  }

  private VitalService vitals() {
    return module().vitals();
  }

  private BackService back() {
    return module().back();
  }

  private PrivateMessageService messages() {
    return module().messages();
  }

  private PlayersModule module() {
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
