package com.valaryrpg.core.module.chat;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Sounds;
import com.valaryrpg.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clear chat, annunci ciclici e applicazione del filtro.
 */
public final class ChatService {

    private final ValaryCorePlugin plugin;
    private ChatFilter filter = ChatFilter.compile(List.of(), List.of(), false, false, List.of());
    private BukkitTask announceTask;
    private int announceIndex;

    public ChatService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        reloadRules();
    }

    public void reloadRules() {
        FileConfiguration yaml = this.plugin.configs().chat();
        this.filter = ChatFilter.compile(
                yaml.getStringList("filter.words"),
                yaml.getStringList("filter.regex"),
                yaml.getBoolean("filter.block-ips", true),
                yaml.getBoolean("filter.block-links", true),
                yaml.getStringList("filter.link-patterns")
        );
        startAnnouncements();
    }

    public void shutdown() {
        stopAnnouncements();
    }

    public void clear(CommandSender staff) {
        int lines = Math.max(0, this.plugin.configs().chat().getInt("clearchat.blank-lines", 120));
        Component blank = Component.empty();
        String staffName = staff.getName();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < lines; i++) {
                player.sendMessage(blank);
            }
            this.plugin.messages().send(player, "chat.cleared", Map.of("player", staffName));
        }
        if (!(staff instanceof Player)) {
            this.plugin.messages().send(staff, "chat.cleared-staff");
        }
        if (staff instanceof Player player) {
            Sounds.play(this.plugin, player, "clearchat");
        }
        if (this.plugin.webhooks() != null) {
            this.plugin.webhooks().log("clearchat", "Chat pulita", staffName);
        }
    }

    public boolean blocked(Player player, String text, boolean priv) {
        FileConfiguration yaml = this.plugin.configs().chat();
        if (!yaml.getBoolean("filter.enabled", true)) {
            logChat(player, text, priv);
            return false;
        }
        if (priv && !yaml.getBoolean("filter.private-messages", true)) {
            return false;
        }
        if (Perms.has(this.plugin, player, "chat-filter-bypass")) {
            logChat(player, text, priv);
            return false;
        }
        Optional<ChatFilter.Hit> hit = this.filter.match(text);
        if (hit.isEmpty()) {
            logChat(player, text, priv);
            return false;
        }
        String key = switch (hit.get()) {
            case WORD -> "chat.filtered-word";
            case REGEX -> "chat.filtered-regex";
            case IP -> "chat.filtered-ip";
            case LINK -> "chat.filtered-link";
        };
        this.plugin.messages().send(player, key);
        Tasks.sync(this.plugin, () -> Sounds.play(this.plugin, player, "chat-filtered"));
        if (yaml.getBoolean("filter.notify-staff", true)) {
            notifyStaff(player, text, hit.get().name().toLowerCase());
        }
        if (this.plugin.webhooks() != null) {
            this.plugin.webhooks().log(
                    "chat-filter",
                    "Filtro chat",
                    player.getName() + " [" + hit.get().name() + "]\n" + text
            );
        }
        return true;
    }

    private void logChat(Player player, String text, boolean priv) {
        if (priv || this.plugin.webhooks() == null) {
            return;
        }
        this.plugin.webhooks().log("chat", "Chat", player.getName() + ": " + text);
    }

    private void notifyStaff(Player source, String text, String reason) {
        Map<String, String> placeholders = Map.of(
                "player", source.getName(),
                "message", this.plugin.messages().escape(text),
                "reason", reason
        );
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            if (Perms.has(this.plugin, staff, "chat-notify")) {
                this.plugin.messages().send(staff, "chat.filter-staff", placeholders);
            }
        }
    }

    private void startAnnouncements() {
        stopAnnouncements();
        this.announceIndex = 0;
        FileConfiguration yaml = this.plugin.configs().chat();
        if (!yaml.getBoolean("announcements.enabled", true)) {
            return;
        }
        List<String> messages = yaml.getStringList("announcements.messages");
        if (messages.isEmpty()) {
            return;
        }
        long ticks = Math.max(20L, yaml.getLong("announcements.interval-seconds", 180L) * 20L);
        this.announceTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::nextAnnounce, ticks, ticks);
    }

    private void stopAnnouncements() {
        if (this.announceTask != null) {
            this.announceTask.cancel();
            this.announceTask = null;
        }
    }

    private void nextAnnounce() {
        List<String> messages = this.plugin.configs().chat().getStringList("announcements.messages");
        if (messages.isEmpty()) {
            return;
        }
        String line;
        if (this.plugin.configs().chat().getBoolean("announcements.random", false)) {
            line = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        } else {
            line = messages.get(Math.floorMod(this.announceIndex++, messages.size()));
        }
        if (line == null || line.isBlank()) {
            return;
        }
        String prefix = this.plugin.configs().root().getString("plugin.prefix", "");
        Component component = this.plugin.messages().deserialize(line.replace("{prefix}", prefix));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
