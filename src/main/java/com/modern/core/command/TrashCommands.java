package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

final class TrashCommands implements Listener {

    private final ModernCorePlugin plugin;
    private final CommandSettings settings;

    TrashCommands(ModernCorePlugin plugin) {
        this.plugin = plugin;
        this.settings = new CommandSettings(plugin);
    }

    void register(Commands registrar) {
        registrar.register(Commands.literal(this.settings.name("commands.trash", "cestino"))
                .requires(stack -> Perms.has(this.plugin, stack.getSender(), "trash"))
                .executes(ctx -> open(ctx.getSource().getSender()))
                .build(), "Butta gli oggetti che non servono", this.settings.aliases("commands.trash-aliases"));
    }

    private int open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.player-only");
            return 0;
        }
        Component title = this.plugin.messages().component("trash.title", Map.of());
        player.openInventory(new TrashHolder(title).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrashHolder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        int amount = count(player.getItemOnCursor());
        player.setItemOnCursor(null);
        Inventory inventory = event.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            amount += count(stack);
        }
        inventory.clear();
        if (amount > 0) {
            this.plugin.messages().send(player, "trash.dropped", Map.of("amount", Integer.toString(amount)));
        }
    }

    private static int count(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        return stack.getAmount();
    }

    private static final class TrashHolder implements InventoryHolder {
        private final Inventory inventory;

        private TrashHolder(Component title) {
            this.inventory = Bukkit.createInventory(this, 54, title);
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
