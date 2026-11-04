package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.features.FeaturesModule;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

final class FeaturesCommands {

  private final ModernCorePlugin plugin;

  FeaturesCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  void register(Commands registrar) {
    registrar.register(command(), "Imposta la skin da un account Minecraft", aliases("commands.skin-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> command() {
    String name = string("commands.skin", "skin");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "skin"))
        .then(Commands.literal("clear")
            .executes(ctx -> clear(ctx.getSource().getSender())))
        .then(Commands.argument("account", StringArgumentType.word())
            .executes(ctx -> apply(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "account"))))
        .executes(ctx -> {
          this.plugin.messages().send(ctx.getSource().getSender(), "features.skin-usage");
          return Command.SINGLE_SUCCESS;
        })
        .build();
  }

  private int apply(CommandSender sender, String account) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    FeaturesModule module = this.plugin.modules().get("features");
    if (module == null || module.skins() == null) {
      this.plugin.messages().send(sender, "general.module-disabled", Map.of("module", "features"));
      return 0;
    }
    module.skins().apply(player, account);
    return Command.SINGLE_SUCCESS;
  }

  private int clear(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    FeaturesModule module = this.plugin.modules().get("features");
    if (module == null || module.skins() == null) {
      this.plugin.messages().send(sender, "general.module-disabled", Map.of("module", "features"));
      return 0;
    }
    module.skins().clear(player);
    return Command.SINGLE_SUCCESS;
  }

  private String string(String path, String fallback) {
    String value = this.plugin.configs().root().getString(path, fallback);
    return value == null || value.isBlank() ? fallback : value;
  }

  private List<String> aliases(String path) {
    return this.plugin.configs().root().getStringList(path);
  }
}
