package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Perms;
import com.valaryrpg.core.util.Placeholders;
import com.valaryrpg.core.util.Tasks;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nickname in RAM, tab, nametag e chat. SQL solo async.
 */
public final class NickService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();

    public NickService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void applyStored(Player player, String stored) {
        apply(player, stored == null || stored.isBlank() ? null : stored);
    }

    public void applyStored(Player player) {
        this.repository.find(player.getUniqueId()).ifPresent(profile -> applyStored(player, profile.nick()));
    }

    public void show(Player player) {
        String nick = this.nicks.get(player.getUniqueId());
        if (nick == null || nick.isBlank()) {
            this.plugin.messages().send(player, "nick.none");
            return;
        }
        this.plugin.messages().send(player, "nick.current", Map.of("nick", nick));
    }

    public void set(Player target, String rawNick, CommandSender initiator) {
        String sanitized = sanitize(initiator, rawNick);
        if (sanitized == null) {
            return;
        }
        apply(target, sanitized);
        Tasks.async(this.plugin, () -> this.repository.updateNick(target.getUniqueId(), target.getName(), sanitized));
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        this.plugin.messages().send(initiator, "nick.set", Map.of("player", target.getName(), "nick", sanitized));
        if (!self) {
            this.plugin.messages().send(target, "nick.set-by-staff", Map.of(
                    "player", initiator.getName(),
                    "nick", sanitized
            ));
        }
    }

    public void clear(Player target, CommandSender initiator) {
        apply(target, null);
        Tasks.async(this.plugin, () -> this.repository.updateNick(target.getUniqueId(), target.getName(), null));
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        this.plugin.messages().send(initiator, "nick.cleared", Map.of("player", target.getName()));
        if (!self) {
            this.plugin.messages().send(target, "nick.cleared-by-staff", Map.of("player", initiator.getName()));
        }
    }

    public String displayMini(Player player) {
        String nick = this.nicks.get(player.getUniqueId());
        if (nick != null && !nick.isBlank()) {
            return nick;
        }
        return this.plugin.messages().escape(player.getName());
    }

    public Component displayComponent(Player player) {
        String nick = this.nicks.get(player.getUniqueId());
        if (nick != null && !nick.isBlank()) {
            return this.plugin.messages().deserialize(nick);
        }
        return Component.text(player.getName());
    }

    public void formatChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component nick = displayComponent(player);
        if (this.plugin.configs().players().getBoolean("chat.enabled", true)) {
            String format = this.plugin.configs().players().getString(
                    "chat.format",
                    "%luckperms_prefix%{displayname} <dark_gray>» <reset>{message}"
            );
            event.renderer(formatted(player, nick, format));
            return;
        }
        if (!this.plugin.configs().players().getBoolean("nick.change-chat", true)) {
            return;
        }
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> previous.render(source, nick, message, viewer));
    }

    public void reapply(Player player) {
        apply(player, this.nicks.get(player.getUniqueId()));
    }

    public void unload(UUID uuid) {
        this.nicks.remove(uuid);
    }

    private ChatRenderer formatted(Player player, Component nick, String format) {
        return (source, sourceDisplayName, message, viewer) -> {
            String filled = Placeholders.apply(player, format == null ? "" : format);
            String display = this.plugin.messages().escape(
                    PlainTextComponentSerializer.plainText().serialize(nick)
            );
            filled = filled
                    .replace("{displayname}", display)
                    .replace("{player}", this.plugin.messages().escape(player.getName()))
                    .replace("{name}", this.plugin.messages().escape(player.getName()));
            int split = filled.indexOf("{message}");
            if (split < 0) {
                return parse(filled).append(Component.space()).append(message);
            }
            Component left = parse(filled.substring(0, split));
            Component right = parse(filled.substring(split + "{message}".length()));
            return left.append(message).append(right);
        };
    }

    private Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.indexOf('&') >= 0 || raw.indexOf('§') >= 0) {
            return LegacyComponentSerializer.legacySection().deserialize(raw.replace('&', '§'));
        }
        return this.plugin.messages().deserialize(raw);
    }

    private void apply(Player player, String nick) {
        if (nick == null || nick.isBlank()) {
            this.nicks.remove(player.getUniqueId());
            player.displayName(null);
            player.playerListName(null);
            player.customName(null);
            player.setCustomNameVisible(false);
            pushTab(player, null);
            return;
        }
        this.nicks.put(player.getUniqueId(), nick);
        Component component = this.plugin.messages().deserialize(nick);
        player.displayName(component);
        if (this.plugin.configs().players().getBoolean("nick.change-tablist", true)) {
            player.playerListName(component);
            pushTab(player, nick);
        }
        if (this.plugin.configs().players().getBoolean("nick.change-nametag", true)) {
            player.customName(component);
            player.setCustomNameVisible(true);
        }
    }

    private void pushTab(Player player, String nickOrNull) {
        TabNickHook.apply(player, nickOrNull);
        Tasks.later(this.plugin, 20L, () -> {
            if (player.isOnline()) {
                TabNickHook.apply(player, nickOrNull);
                if (nickOrNull != null) {
                    player.playerListName(this.plugin.messages().deserialize(nickOrNull));
                }
            }
        });
        Tasks.later(this.plugin, 40L, () -> {
            if (player.isOnline()) {
                TabNickHook.apply(player, nickOrNull);
            }
        });
    }

    private String sanitize(CommandSender initiator, String rawNick) {
        String trimmed = rawNick == null ? "" : rawNick.trim();
        if (trimmed.isBlank()) {
            this.plugin.messages().send(initiator, "nick.invalid");
            return null;
        }
        boolean colors = !(initiator instanceof Player) || Perms.has(this.plugin, initiator, "nick-color");
        String mini = colors ? trimmed : MiniMessage.miniMessage().stripTags(trimmed);
        String plain = PlainTextComponentSerializer.plainText().serialize(this.plugin.messages().deserialize(mini));
        int max = this.plugin.configs().players().getInt("nick.max-length", 32);
        if (plain.length() > max) {
            this.plugin.messages().send(initiator, "nick.too-long", Map.of("max", String.valueOf(max)));
            return null;
        }
        List<String> blocked = this.plugin.configs().players().getStringList("nick.blocked-words");
        String lower = plain.toLowerCase(Locale.ROOT);
        for (String word : blocked) {
            if (word != null && !word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) {
                this.plugin.messages().send(initiator, "nick.blocked");
                return null;
            }
        }
        return mini;
    }
}
