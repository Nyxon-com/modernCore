package com.modern.core.pack;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class PackJoinSender implements Listener {
    private static final UUID PACK_ID = UUID.fromString("a1c4e8b2-7d3f-4a96-9c15-2b6e0f8d4c71");

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
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(URI.create(url))
                .hash(sha1)
                .build();
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(true)
                .replace(true)
                .prompt(Component.text("Accetta il pack di modern smp"))
                .build());
            this.plugin.getLogger().info("Pack inviata a " + player.getName());
        } catch (Exception error) {
            this.plugin.getLogger().warning("Pack non inviata a " + player.getName() + ": " + error.getMessage());
        }
    }
}
