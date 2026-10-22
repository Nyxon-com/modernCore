//made with love by nyxon

package com.modern.core.pack;

import com.modern.core.ModernCorePlugin;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackJoinSender implements Listener {
  private static final UUID PACK_ID = UUID.fromString("c3b91e70-5a24-4f68-8d12-6e0a4b7c9d53");
  private static final int CLIENT_PACK_FORMAT = 46;
  private static final int PORT = 8088;

  private final ModernCorePlugin plugin;
  private HttpServer server;
  private byte[] pack;
  private String sha1 = "";

  public PackJoinSender(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    try {
      this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
      this.server.createContext("/pack.zip", exchange -> {
        byte[] body = this.pack == null ? new byte[0] : this.pack;
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      this.server.start();
      this.plugin.getLogger().info("Pack in ascolto su http://127.0.0.1:" + PORT + "/pack.zip");
    } catch (Exception error) {
      this.plugin.getLogger().warning("Pack non in ascolto: " + error.getMessage());
    }
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  public void stop() {
    if (this.server != null) {
      this.server.stop(0);
    }
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
    if (!player.isOnline() || this.server == null) {
      return;
    }
    Path zip = this.plugin.getDataFolder().toPath().getParent().resolve("Nexo").resolve("pack").resolve("pack.zip");
    if (!Files.isRegularFile(zip)) {
      this.plugin.getLogger().warning("Pack non inviata, manca " + zip);
      return;
    }
    try {
      byte[] patched = patch(Files.readAllBytes(zip));
      this.pack = patched;
      this.sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(patched));
      ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
        .id(PACK_ID)
        .uri(URI.create("http://127.0.0.1:" + PORT + "/pack.zip"))
        .hash(this.sha1)
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

  private byte[] patch(byte[] source) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(source));
       ZipOutputStream output = new ZipOutputStream(buffer)) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        byte[] content = input.readAllBytes();
        if ("pack.mcmeta".equals(entry.getName())) {
          content = addPackFormat(content);
        }
        output.putNextEntry(new ZipEntry(entry.getName()));
        output.write(content);
        output.closeEntry();
      }
    }
    return buffer.toByteArray();
  }

  private byte[] addPackFormat(byte[] content) {
    String json = new String(content, StandardCharsets.UTF_8);
    String root = "\"pack\":{\"description\":\"Nexo's ResourcePack\",\"min_format\":88,\"max_format\":199}";
    String compatible = "\"pack\":{\"description\":\"Nexo's ResourcePack\",\"pack_format\":" + CLIENT_PACK_FORMAT
      + ",\"supported_formats\":{\"min_inclusive\":46,\"max_inclusive\":199},\"min_format\":46,\"max_format\":199}";
    if (json.contains(root)) {
      json = json.replace(root, compatible);
      this.plugin.getLogger().info("Pack compatibile da 1.21.4 a 26.2");
    }
    return json.getBytes(StandardCharsets.UTF_8);
  }
}
