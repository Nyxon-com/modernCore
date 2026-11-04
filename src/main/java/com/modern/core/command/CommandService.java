package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class CommandService {

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;
  private final CommandFilter filter;

  public CommandService(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
    this.filter = new CommandFilter(plugin);
  }

  public void register() {
    TrashCommands trash = new TrashCommands(this.plugin);
    this.plugin.getServer().getPluginManager().registerEvents(trash, this.plugin);
    this.plugin.getServer().getPluginManager().registerEvents(
        new CommandFilterListener(this.plugin, this.filter),
        this.plugin
    );
    this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
      Commands registrar = event.registrar();
      registrar.register(rootCommand(), "Comandi amministrativi modernCore", aliases("commands.root-aliases"));
      if (enabled("worlds")) {
        new WorldsCommands(this.plugin).register(registrar);
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
      if (enabled("holograms")) {
        new HologramCommands(this.plugin).register(registrar);
      }
      if (enabled("kits")) {
        new KitsCommands(this.plugin).register(registrar);
      }
      if (enabled("sell")) {
        new SellCommands(this.plugin).register(registrar);
      }
      trash.register(registrar);
      new ReciclatoreCommands(this.plugin).register(registrar);
      if (enabled("chat")) {
        new ChatCommands(this.plugin).register(registrar);
      }
      if (enabled("features")) {
        new FeaturesCommands(this.plugin).register(registrar);
      }
      new CommandListCommands(this.plugin, this.filter).register(registrar);
    });
  }

  public void reloadFilter() {
    this.filter.reload();
    for (Player player : this.plugin.getServer().getOnlinePlayers()) {
      player.updateCommands();
    }
  }

  private LiteralCommandNode<CommandSourceStack> rootCommand() {
    String name = string("commands.root", "valary");
    return Commands.literal(name)
        .then(Commands.literal("reload")
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), "reload"))
            .executes(ctx -> {
              CommandSender sender = ctx.getSource().getSender();
              ModernCorePlugin.ReloadResult result = this.plugin.reloadAll();
              if (result.success()) {
                this.plugin.messages().send(sender, "reload.success", Map.of(
                    "files", String.valueOf(result.files()),
                    "holograms", String.valueOf(result.holograms()),
                    "triggers", String.valueOf(result.triggers()),
                    "modules", String.valueOf(result.modules())
                ));
                if (sender instanceof Player player) {
                  Sounds.play(this.plugin, player, "reload-success");
                }
                if (result.storageRestart() || result.commandsRestart()) {
                  this.plugin.messages().send(sender, "reload.restart-required");
                }
              } else {
                this.plugin.messages().send(sender, "reload.failed", Map.of("error", result.error()));
              }
              return Command.SINGLE_SUCCESS;
            }))
        .then(Commands.literal("fixperms")
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), "admin")
                || stack.getSender().isOp())
            .executes(ctx -> {
              new com.modern.core.security.OwnerPermsBootstrap(this.plugin).apply();
              this.plugin.messages().send(ctx.getSource().getSender(), "security.fixperms-done");
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

  private boolean enabled(String moduleId) {
    return this.plugin.modules() != null && this.plugin.modules().isEnabled(moduleId);
  }

  private String string(String path, String fallback) {
    return this.settings.name(path, fallback);
  }

  private List<String> aliases(String path) {
    return this.settings.aliases(path);
  }
}
