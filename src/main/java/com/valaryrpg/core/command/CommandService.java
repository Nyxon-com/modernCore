package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Sounds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Registrazione comandi Paper/Brigadier. Nomi, alias e permessi arrivano da config.yml.
 */
public final class CommandService {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;
    private final CommandFilter filter;

    public CommandService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
        this.filter = new CommandFilter(plugin);
    }

    public void register() {
        this.plugin.getServer().getPluginManager().registerEvents(
                new CommandFilterListener(this.plugin, this.filter),
                this.plugin
        );
        this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(rootCommand(), "Comandi amministrativi ValaryRPG-Core", aliases("commands.root-aliases"));
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
            if (enabled("chat")) {
                new ChatCommands(this.plugin).register(registrar);
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
                            ValaryCorePlugin.ReloadResult result = this.plugin.reloadAll();
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
