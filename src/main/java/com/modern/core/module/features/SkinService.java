package com.modern.core.module.features;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public final class SkinService {

  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  private final ModernCorePlugin plugin;

  public SkinService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void apply(Player player, String account) {
    String name = account.trim();
    if (!name.matches("[A-Za-z0-9_]{1,16}")) {
      this.plugin.messages().send(player, "features.skin-invalid", Map.of("skin", name));
      return;
    }
    Tasks.async(this.plugin, () -> {
      Textures textures = fetch(name);
      Tasks.sync(this.plugin, () -> finish(player, name, textures));
    });
  }

  public void clear(Player player) {
    PlayerProfile own = player.getPlayerProfile();
    own.removeProperty("textures");
    player.setPlayerProfile(own);
    refresh(player);
    this.plugin.messages().send(player, "features.skin-cleared");
  }

  private void finish(Player player, String name, Textures textures) {
    if (!player.isOnline()) {
      return;
    }
    if (textures == null) {
      this.plugin.messages().send(player, "features.skin-missing", Map.of("skin", name));
      return;
    }
    PlayerProfile own = player.getPlayerProfile();
    own.removeProperty("textures");
    own.setProperty(new ProfileProperty("textures", textures.value, textures.signature));
    player.setPlayerProfile(own);
    refresh(player);
    this.plugin.messages().send(player, "features.skin-set", Map.of("skin", name.toLowerCase(Locale.ROOT)));
  }

  private void refresh(Player player) {
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.getUniqueId().equals(player.getUniqueId())) {
        continue;
      }
      viewer.hideEntity(this.plugin, player);
      viewer.showEntity(this.plugin, player);
    }
  }

  private Textures fetch(String name) {
    String profile = get("https://api.mojang.com/users/profiles/minecraft/" + name);
    if (profile == null) {
      return null;
    }
    String id = field(profile, "id");
    if (id == null || !id.matches("[0-9a-fA-F]{32}")) {
      return null;
    }
    String session = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
    if (session == null) {
      return null;
    }
    String value = field(session, "value");
    if (value == null || value.isBlank()) {
      return null;
    }
    return new Textures(value, field(session, "signature"));
  }

  private String get(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "ValaryRPG")
          .GET()
          .build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      return response.body();
    } catch (Exception exception) {
      this.plugin.getLogger().warning("Skin non letta: " + exception.getMessage());
      return null;
    }
  }

  private static String field(String json, String key) {
    String mark = "\"" + key + "\"";
    int at = json.indexOf(mark);
    if (at < 0) {
      return null;
    }
    int colon = json.indexOf(':', at + mark.length());
    if (colon < 0) {
      return null;
    }
    int start = json.indexOf('"', colon + 1);
    if (start < 0) {
      return null;
    }
    int end = json.indexOf('"', start + 1);
    if (end < 0) {
      return null;
    }
    return json.substring(start + 1, end);
  }

  private record Textures(String value, String signature) {
  }
}
