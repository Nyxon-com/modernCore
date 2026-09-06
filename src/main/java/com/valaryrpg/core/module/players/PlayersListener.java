package com.valaryrpg.core.module.players;

import com.valaryrpg.core.ValaryCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.util.Map;

public final class PlayersListener implements Listener {

    private final ValaryCorePlugin plugin;
    private final PlayerDataRepository repository;
    private final NickService nicks;
    private final FlyService fly;
    private final PrivateMessageService messages;

    public PlayersListener(
            ValaryCorePlugin plugin,
            PlayerDataRepository repository,
            NickService nicks,
            FlyService fly,
            PrivateMessageService messages
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.nicks = nicks;
        this.fly = fly;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = ipOf(player);
        this.repository.touchJoin(player.getUniqueId(), player.getName(), ip, System.currentTimeMillis());
        this.nicks.applyStored(player);
        this.fly.restore(player);
        this.messages.loadSpy(player);

        if (!this.plugin.configs().players().getBoolean("join-quit.enabled", true)) {
            return;
        }
        boolean first = !player.hasPlayedBefore();
        String key = first ? "join-quit.first-join" : "join-quit.join";
        Component message = this.plugin.messages().component(key, Map.of(
                "player", this.plugin.messages().escape(player.getName()),
                "displayname", this.nicks.displayMini(player)
        ));
        event.joinMessage(blank(key) ? null : message);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.messages.unload(player.getUniqueId());
        if (!this.plugin.configs().players().getBoolean("join-quit.enabled", true)) {
            return;
        }
        Component message = this.plugin.messages().component("join-quit.quit", Map.of(
                "player", this.plugin.messages().escape(player.getName()),
                "displayname", this.nicks.displayMini(player)
        ));
        event.quitMessage(blank("join-quit.quit") ? null : message);
    }

    private boolean blank(String key) {
        String raw = this.plugin.configs().messages().getString(key, "");
        return raw == null || raw.isBlank();
    }

    private static String ipOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "";
        }
        return address.getAddress().getHostAddress();
    }
}
