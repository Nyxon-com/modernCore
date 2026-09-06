package com.valaryrpg.core.command;

import com.mojang.brigadier.Command;
import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.module.sell.SellModule;
import com.valaryrpg.core.util.Perms;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class SellCommands {

    private final ValaryCorePlugin plugin;
    private final CommandSettings settings;

    SellCommands(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    void register(io.papermc.paper.command.brigadier.Commands registrar) {
        String name = this.settings.name("commands.sell", "sell");
        registrar.register(Commands.literal(name)
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "sell"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        this.plugin.messages().send(sender, "general.player-only");
                        return 0;
                    }
                    module().sell().open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(), "Vendi oggetti", this.settings.aliases("commands.sell-aliases"));
    }

    private SellModule module() {
        return this.plugin.modules().get("sell");
    }
}
