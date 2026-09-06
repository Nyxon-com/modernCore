package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.util.Perms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NickService {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;

    public NickService(ValaryCorePlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void applyStored(Player player) {
        this.repository.find(player.getUniqueId()).ifPresent(profile -> apply(player, profile.nick()));
    }

    public void show(Player player) {
        this.repository.find(player.getUniqueId()).ifPresentOrElse(profile -> {
            if (profile.nick() == null || profile.nick().isBlank()) {
                this.plugin.messages().send(player, "nick.none");
                return;
            }
            this.plugin.messages().send(player, "nick.current", Map.of("nick", profile.nick()));
        }, () -> this.plugin.messages().send(player, "nick.none"));
    }

    public void set(Player target, String rawNick, CommandSender initiator) {
        String sanitized = sanitize(initiator, rawNick);
        if (sanitized == null) {
            return;
        }
        this.repository.updateNick(target.getUniqueId(), sanitized);
        apply(target, sanitized);
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
        this.repository.updateNick(target.getUniqueId(), null);
        apply(target, null);
        boolean self = initiator instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        this.plugin.messages().send(initiator, "nick.cleared", Map.of("player", target.getName()));
        if (!self) {
            this.plugin.messages().send(target, "nick.cleared-by-staff", Map.of("player", initiator.getName()));
        }
    }

    public String displayMini(Player player) {
        return this.repository.find(player.getUniqueId())
                .map(PlayerProfile::nick)
                .filter(nick -> nick != null && !nick.isBlank())
                .orElseGet(() -> this.plugin.messages().escape(player.getName()));
    }

    private void apply(Player player, String nick) {
        boolean tab = this.plugin.configs().players().getBoolean("nick.change-tablist", true);
        if (nick == null || nick.isBlank()) {
            player.displayName(null);
            if (tab) {
                player.playerListName(null);
            }
            return;
        }
        Component component = this.plugin.messages().deserialize(nick);
        player.displayName(component);
        if (tab) {
            player.playerListName(component);
        }
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
