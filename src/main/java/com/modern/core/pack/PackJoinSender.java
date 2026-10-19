package com.modern.core.pack;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Invia la pack di Nexo dopo l'ingresso.
 * Il prompt di server.properties e l'invio di Nexo in configurazione non partono:
 * LuckPerms, DeluxeMenus e WorldGuard ascoltano il login e Paper salta quella fase.
 */
public final class PackJoinSender implements Listener {

    private static final UUID PACK_ID = UUID.fromString("6f0c2a14-9b7e-4d55-8c31-2e7a1b0d4f90");

    private final ModernCorePlugin plugin;

    public PackJoinSender(ModernCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> send(player), 40L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        this.plugin.getLogger().info("Pack di " + event.getPlayer().getName() + ": " + event.getStatus());
    }

    private void send(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Path zip = this.plugin.getDataFolder().toPath().getParent().resolve("Nexo").resolve("pack").resolve("pack.zip");
        if (!Files.isRegularFile(zip)) {
            this.plugin.getLogger().warning("Pack non inviata, manca " + zip);
            return;
        }
        try {
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(zip)));
            String url = "https://hermes.nexomc.com/pack/" + sha1;
            player.setResourcePack(
                    PACK_ID,
                    url,
                    HexFormat.of().parseHex(sha1),
                    Component.text("Accetta il pack di modern smp"),
                    true
            );
            this.plugin.getLogger().info("Pack inviata a " + player.getName());
        } catch (Exception error) {
            this.plugin.getLogger().warning("Pack non inviata a " + player.getName() + ": " + error.getMessage());
        }
    }
}
