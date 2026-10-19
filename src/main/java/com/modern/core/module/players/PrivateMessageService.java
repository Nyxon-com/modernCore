package com.modern.core.module.players;

import com.modern.core.ModernCorePlugin;
import com.modern.core.module.chat.ChatModule;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Messaggi privati: /msg, /r e socialspy.
 */
public final class PrivateMessageService {

    private final ModernCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final NickService nicks;
    private final Map<UUID, UUID> lastReply = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spyCache = new ConcurrentHashMap<>();

    public PrivateMessageService(ModernCorePlugin plugin, PlayerDataRepository repository, NickService nicks) {
        this.plugin = plugin;
        this.repository = repository;
        this.nicks = nicks;
    }

    public void send(CommandSender sender, Player target, String message) {
        if (message == null || message.isBlank()) {
            this.plugin.messages().send(sender, "msg.usage", Map.of("label", label("msg")));
            return;
        }
        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())
                && !this.plugin.configs().players().getBoolean("msg.allow-self", false)) {
            this.plugin.messages().send(sender, "msg.self");
            return;
        }
        if (sender instanceof Player player && blockedPrivate(player, message)) {
            return;
        }

        String escaped = this.plugin.messages().escape(message);
        String senderName = display(sender);
        String targetName = this.nicks.displayMini(target);

        this.plugin.messages().send(sender, "msg.to-sender", Map.of(
                "target", targetName,
                "player", senderName,
                "message", escaped
        ));
        this.plugin.messages().send(target, "msg.to-target", Map.of(
                "target", targetName,
                "player", senderName,
                "message", escaped
        ));

        if (sender instanceof Player player) {
            this.lastReply.put(player.getUniqueId(), target.getUniqueId());
            this.lastReply.put(target.getUniqueId(), player.getUniqueId());
            Sounds.play(this.plugin, player, "msg-send");
        }
        Sounds.play(this.plugin, target, "msg-receive");
        spy(sender, target, escaped, senderName, targetName);
        if (this.plugin.webhooks() != null) {
            this.plugin.webhooks().log(
                    "msg",
                    "Messaggio privato",
                    sender.getName() + " → " + target.getName() + "\n" + message
            );
        }
    }

    public void reply(Player sender, String message) {
        UUID last = this.lastReply.get(sender.getUniqueId());
        if (last == null) {
            this.plugin.messages().send(sender, "msg.no-reply");
            return;
        }
        Player target = Bukkit.getPlayer(last);
        if (target == null) {
            this.plugin.messages().send(sender, "msg.reply-offline");
            return;
        }
        send(sender, target, message);
    }

    public void toggleSpy(Player player) {
        boolean next = !isSpy(player);
        this.spyCache.put(player.getUniqueId(), next);
        Tasks.async(this.plugin, () -> this.repository.updateSocialSpy(player.getUniqueId(), player.getName(), next));
        this.plugin.messages().send(player, next ? "msg.spy-on" : "msg.spy-off");
    }

    public void loadSpy(Player player, boolean enabled) {
        this.spyCache.put(player.getUniqueId(), enabled);
    }

    public void loadSpy(Player player) {
        this.repository.find(player.getUniqueId()).ifPresent(profile -> loadSpy(player, profile.socialSpy()));
    }

    public void unload(UUID uuid) {
        this.spyCache.remove(uuid);
        this.lastReply.remove(uuid);
    }

    private boolean isSpy(Player player) {
        return this.spyCache.getOrDefault(player.getUniqueId(), false);
    }

    private void spy(CommandSender sender, Player target, String escaped, String senderName, String targetName) {
        if (!this.plugin.configs().players().getBoolean("msg.socialspy", true)) {
            return;
        }
        boolean spySelf = this.plugin.configs().players().getBoolean("msg.spy-self", true);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!Perms.has(this.plugin, staff, "socialspy") || !isSpy(staff)) {
                continue;
            }
            boolean involved = staff.equals(sender) || staff.equals(target);
            if (involved && !spySelf) {
                continue;
            }
            this.plugin.messages().send(staff, involved ? "msg.spy-self-line" : "msg.spy", Map.of(
                    "player", senderName,
                    "target", targetName,
                    "message", escaped
            ));
        }
    }

    private boolean blockedPrivate(Player sender, String message) {
        if (this.plugin.modules() == null || !this.plugin.modules().isEnabled("chat")) {
            return false;
        }
        ChatModule module = this.plugin.modules().get("chat");
        return module != null && module.chat() != null && module.chat().blocked(sender, message, true);
    }

    private String display(CommandSender sender) {
        if (sender instanceof Player player) {
            return this.nicks.displayMini(player);
        }
        return this.plugin.messages().escape(sender.getName());
    }

    private String label(String key) {
        return this.plugin.configs().root().getString("commands." + key, key);
    }
}
