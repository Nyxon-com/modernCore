package com.valaryrpg.core.webhook;

import com.valaryrpg.core.ValaryCorePlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comandi, testi e chiavi player verso i webhook Discord.
 * La chat pubblica è già in {@code ChatService}.
 */
public final class WebhookActivityListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final long KEY_COOLDOWN_MILLIS = 2500L;

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Long> keyCooldown = new ConcurrentHashMap<>();

    public WebhookActivityListener(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        String label = normalizeLabel(message.split("\\s+")[0]);
        Command command = this.plugin.getServer().getCommandMap().getCommand(label);
        if (command == null && !event.isCancelled()) {
            return;
        }
        if (event.isCancelled()) {
            this.plugin.webhooks().log("command-blocked", "Comando bloccato", player.getName() + "\n" + message);
            return;
        }
        this.plugin.webhooks().log("commands", "Comando", player.getName() + "\n" + message);
        if (isCrateLabel(label) || message.toLowerCase(Locale.ROOT).contains("crate")) {
            this.plugin.webhooks().log("keys", "Crate / key", player.getName() + "\n" + message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnknown(UnknownCommandEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        if (!(event.getSender() instanceof Player player)) {
            return;
        }
        String line = event.getCommandLine();
        this.plugin.webhooks().log(
                "command-unknown",
                "Comando sconosciuto",
                player.getName() + "\n/" + (line == null ? "" : line)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < event.lines().size(); i++) {
            String line = PLAIN.serialize(event.line(i));
            if (line.isBlank()) {
                continue;
            }
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            lines.append(i + 1).append(": ").append(line);
        }
        if (lines.isEmpty()) {
            return;
        }
        this.plugin.webhooks().log("signs", "Cartello", event.getPlayer().getName() + "\n" + lines);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        String title = meta.title() == null ? "" : PLAIN.serialize(meta.title());
        StringBuilder pages = new StringBuilder();
        int count = Math.min(meta.getPageCount(), 6);
        for (int i = 1; i <= count; i++) {
            String page = PLAIN.serialize(meta.page(i)).trim();
            if (page.isEmpty()) {
                continue;
            }
            if (page.length() > 200) {
                page = page.substring(0, 200) + "…";
            }
            if (!pages.isEmpty()) {
                pages.append('\n');
            }
            pages.append("p").append(i).append(": ").append(page);
        }
        if (title.isBlank() && pages.isEmpty()) {
            return;
        }
        this.plugin.webhooks().log(
                "signs",
                event.isSigning() ? "Libro firmato" : "Libro",
                event.getPlayer().getName() + (title.isBlank() ? "" : "\n" + title) + (pages.isEmpty() ? "" : "\n" + pages)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKeyUse(PlayerInteractEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction().isLeftClick()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        String blob = itemBlob(item);
        if (!looksLikeKey(blob)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.keyCooldown.put(event.getPlayer().getUniqueId(), now);
        if (last != null && now - last < KEY_COOLDOWN_MILLIS) {
            return;
        }
        this.plugin.webhooks().log("keys", "Chiave usata", event.getPlayer().getName() + "\n" + PLAIN.serialize(item.displayName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (this.plugin.webhooks() != null) {
            this.plugin.webhooks().log("join", "Join", event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.keyCooldown.remove(event.getPlayer().getUniqueId());
        if (this.plugin.webhooks() != null) {
            this.plugin.webhooks().log("quit", "Quit", event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (this.plugin.webhooks() == null) {
            return;
        }
        Player player = event.getEntity();
        String killer = player.getKiller() == null ? "" : " killer=" + player.getKiller().getName();
        this.plugin.webhooks().log("death", "Morte", player.getName() + killer);
    }

    private static boolean isCrateLabel(String label) {
        return label.equals("crate")
                || label.equals("crates")
                || label.equals("bosscrates")
                || label.equals("key")
                || label.equals("keys")
                || label.equals("chiave");
    }

    private static String itemBlob(ItemStack item) {
        StringBuilder blob = new StringBuilder(PLAIN.serialize(item.displayName()).toLowerCase(Locale.ROOT));
        var meta = item.getItemMeta();
        if (meta != null && meta.hasLore() && meta.lore() != null) {
            for (var line : meta.lore()) {
                blob.append(' ').append(PLAIN.serialize(line).toLowerCase(Locale.ROOT));
            }
        }
        blob.append(' ').append(item.getType().name().toLowerCase(Locale.ROOT));
        return blob.toString();
    }

    private static boolean looksLikeKey(String blob) {
        return blob.contains("chiave")
                || blob.contains("ᴄʜɪᴀᴠᴇ")
                || blob.contains("key")
                || blob.contains("crate")
                || blob.contains("ᴄʀᴀᴛᴇ");
    }

    private static String normalizeLabel(String token) {
        if (token == null) {
            return "";
        }
        String value = token.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value;
    }
}
