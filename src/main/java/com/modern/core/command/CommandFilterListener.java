package com.modern.core.command;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.tree.CommandNode;
import com.modern.core.ModernCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab Brigadier + digitazione: toglie i comandi filtrati e applica cooldown anti-spam.
 */
final class CommandFilterListener implements Listener {

    private final ModernCorePlugin plugin;
    private final CommandFilter filter;

    CommandFilterListener(ModernCorePlugin plugin, CommandFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (this.filter.seeAll(player)) {
            return;
        }
        event.getCommands().removeIf(name -> hidden(player, name));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBrigadier(AsyncPlayerSendCommandsEvent<?> event) {
        if (!event.hasFiredAsync()) {
            return;
        }
        if (this.filter.seeAll(event.getPlayer())) {
            return;
        }
        strip(event.getCommandNode(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        String label = CommandFilter.normalize(message.split("\\s+")[0]);
        if (!this.filter.seeAll(player) && hidden(player, label)) {
            event.setCancelled(true);
            this.plugin.messages().send(player, "general.no-permission");
            return;
        }
        double wait = this.filter.tryCommand(player, label);
        if (wait > 0.0D) {
            event.setCancelled(true);
            String seconds = String.format(Locale.ROOT, "%.1f", wait);
            this.plugin.messages().send(player, "general.cooldown", Map.of("seconds", seconds));
        }
    }

    private boolean hidden(Player player, String name) {
        if (this.filter.seeAll(player)) {
            return false;
        }
        if (name != null && name.indexOf(':') >= 0) {
            return true;
        }
        Command command = this.plugin.getServer().getCommandMap().getCommand(name);
        return !this.filter.allowed(player, name, command);
    }

    private void strip(CommandNode<?> root, Player player) {
        List<String> drop = new ArrayList<>();
        for (CommandNode<?> child : List.copyOf(root.getChildren())) {
            String name = child.getName();
            if (hidden(player, name)) {
                drop.add(name);
            }
        }
        for (String name : drop) {
            removeChild(root, name);
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeChild(CommandNode<?> root, String name) {
        try {
            Field children = CommandNode.class.getDeclaredField("children");
            Field literals = CommandNode.class.getDeclaredField("literals");
            Field arguments = CommandNode.class.getDeclaredField("arguments");
            children.setAccessible(true);
            literals.setAccessible(true);
            arguments.setAccessible(true);
            ((Map<String, ?>) children.get(root)).remove(name);
            ((Map<String, ?>) literals.get(root)).remove(name);
            ((Map<String, ?>) arguments.get(root)).remove(name);
        } catch (ReflectiveOperationException ignored) {
            // Paper/Brigadier interno diverso: resta PlayerCommandSendEvent
        }
    }
}
