package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * /command list — elenco comandi utili, copiabile. /command list all per lo staff.
 */
final class CommandListCommands {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;
    private final CommandFilter filter;

    CommandListCommands(ValaryCorePlugin plugin, CommandFilter filter) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
        this.filter = filter;
    }

    void register(Commands registrar) {
        registrar.register(root(), "Elenco comandi in base ai tuoi permessi", this.settings.aliases("commands.command-aliases"));
    }

    private LiteralCommandNode<CommandSourceStack> root() {
        String name = this.settings.name("commands.command", "command");
        return Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "command-list"))
                .executes(ctx -> {
                    this.plugin.messages().send(ctx.getSource().getSender(), "command-list.usage", Map.of(
                            "label", name
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list")
                        .executes(ctx -> show(ctx.getSource().getSender(), false))
                        .then(Commands.literal("all")
                                .requires(stack -> this.filter.seeAll(stack.getSender()))
                                .executes(ctx -> show(ctx.getSource().getSender(), true))))
                .build();
    }

    private int show(CommandSender sender, boolean dumpAll) {
        List<String> allowed = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        collect(sender, allowed, denied, dumpAll);
        String dump = dumpAll ? dump(allowed, denied) : dumpAllowed(allowed);

        sender.sendMessage(this.plugin.messages().component("command-list.copied", Map.of(
                "player", this.plugin.messages().escape(sender.getName()),
                "allowed", String.valueOf(allowed.size()),
                "denied", String.valueOf(denied.size()),
                "total", String.valueOf(allowed.size() + denied.size())
        )).clickEvent(ClickEvent.copyToClipboard(dump)).hoverEvent(HoverEvent.showText(
                this.plugin.messages().component("command-list.hover", Map.of())
        )));

        if (!(sender instanceof Player)) {
            sender.sendMessage(dump);
        }
        return Command.SINGLE_SUCCESS;
    }

    static String dump(List<String> allowed, List<String> denied) {
        return dumpAllowed(allowed) + "\n\n" + dumpDenied(denied);
    }

    static String dumpAllowed(List<String> allowed) {
        StringBuilder text = new StringBuilder();
        text.append("Puoi usare (").append(allowed.size()).append("):\n");
        for (String name : allowed) {
            text.append('/').append(name).append('\n');
        }
        return text.toString().stripTrailing();
    }

    static String dumpDenied(List<String> denied) {
        StringBuilder text = new StringBuilder();
        text.append("Non puoi usare (").append(denied.size()).append("):\n");
        for (String name : denied) {
            text.append('/').append(name).append('\n');
        }
        return text.toString().stripTrailing();
    }

    private void collect(CommandSender sender, List<String> allowed, List<String> denied, boolean dumpAll) {
        TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, org.bukkit.command.Command> known = this.plugin.getServer().getCommandMap().getKnownCommands();
        for (Map.Entry<String, org.bukkit.command.Command> entry : known.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.indexOf(':') >= 0) {
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT);
            if (!seen.add(name)) {
                continue;
            }
            org.bukkit.command.Command command = entry.getValue();
            if (command == null) {
                continue;
            }
            boolean usable = command.testPermissionSilent(sender);
            boolean listed = dumpAll || this.filter.playerVisible(sender, name, command);
            if (!listed) {
                continue;
            }
            if (usable) {
                allowed.add(name);
            } else if (dumpAll) {
                denied.add(name);
            }
        }
        allowed.sort(Comparator.naturalOrder());
        denied.sort(Comparator.naturalOrder());
    }
}
