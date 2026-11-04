package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.module.chat.ChatModule;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.List;

final class ChatCommands {

  private final ModernCorePlugin plugin;

  ChatCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  void register(Commands registrar) {
    registrar.register(clearCommand(), "Pulisce la chat di tutti i giocatori", aliases("commands.clearchat-aliases"));
  }

  private LiteralCommandNode<CommandSourceStack> clearCommand() {
    String name = string("commands.clearchat", "clearchat");
    return Commands.literal(name)
        .requires(stack -> Perms.has(this.plugin, stack.getSender(), "clearchat"))
        .executes(ctx -> {
          CommandSender sender = ctx.getSource().getSender();
          ChatModule module = this.plugin.modules().get("chat");
          if (module == null || module.chat() == null) {
            return Command.SINGLE_SUCCESS;
          }
          module.chat().clear(sender);
          return Command.SINGLE_SUCCESS;
        })
        .build();
  }

  private String string(String path, String fallback) {
    String value = this.plugin.configs().root().getString(path, fallback);
    return value == null || value.isBlank() ? fallback : value;
  }

  private List<String> aliases(String path) {
    return this.plugin.configs().root().getStringList(path);
  }
}
