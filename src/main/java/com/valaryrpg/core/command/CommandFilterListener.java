package com.valaryrpg.core.command;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Tab-complete e digitazione: toglie i comandi filtrati ai player.
 */
final class CommandFilterListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final CommandFilter filter;

    CommandFilterListener(ValaryCorePlugin plugin, CommandFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (this.filter.seeAll(event.getPlayer())) {
            return;
        }
        event.getCommands().removeIf(name -> {
            if (name != null && name.indexOf(':') >= 0) {
                return true;
            }
            Command command = this.plugin.getServer().getCommandMap().getCommand(name);
            return !this.filter.allowed(event.getPlayer(), name, command);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (this.filter.seeAll(event.getPlayer())) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        String label = CommandFilter.normalize(message.split("\\s+")[0]);
        Command command = this.plugin.getServer().getCommandMap().getCommand(label);
        if (this.filter.allowed(event.getPlayer(), label, command)) {
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "general.no-permission");
    }
}
